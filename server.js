
// Simple Node.js + Express server with SQLite for demo purposes
const express = require('express');
const bodyParser = require('body-parser');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');
const cors = require('cors');

const DB_FILE = path.join(__dirname, 'inventory.db');
const app = express();
app.use(cors());
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, 'client')));

// Init DB
const dbExists = fs.existsSync(DB_FILE);
const db = new sqlite3.Database(DB_FILE);
db.serialize(() => {
  if (!dbExists) {
    db.run(`CREATE TABLE suppliers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      contact TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );`);

    db.run(`CREATE TABLE inventory (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      sku TEXT NOT NULL UNIQUE,
      category TEXT,
      quantity INTEGER NOT NULL DEFAULT 0,
      unit_price REAL,
      supplier_id INTEGER,
      location TEXT,
      min_stock INTEGER DEFAULT 0,
      notes TEXT,
      deleted INTEGER DEFAULT 0,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
    );`);

    db.run(`CREATE TABLE inventory_audit (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      inventory_id INTEGER,
      action TEXT NOT NULL,
      performed_by INTEGER,
      performed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      before_state TEXT,
      after_state TEXT,
      reason TEXT
    );`);

    // seed
    db.run(`INSERT INTO suppliers (name, contact) VALUES ('Acme Supplies', '{"phone":"9999999999"}')`);
    db.run(`INSERT INTO inventory (name, sku, category, quantity, unit_price, supplier_id, location, min_stock, notes) VALUES
      ('Widget A','WIDGET-A-001','Widgets',100,49.99,1,'WH-01-R01',10,'First batch'),
      ('Gadget B','GADGET-B-001','Gadgets',50,79.50,1,'WH-02-R03',5,'')`);
  }
});

// Helpers
function audit(inventory_id, action, before, after, reason=null){
  db.run(`INSERT INTO inventory_audit (inventory_id, action, before_state, after_state, reason) VALUES (?,?,?,?,?)`,
    [inventory_id, action, JSON.stringify(before||{}), JSON.stringify(after||{}), reason], (err)=>{});
}

// Routes
app.get('/api/v1/inventory', (req, res) => {
  const { category, location, q } = req.query;
  let sql = "SELECT * FROM inventory WHERE deleted=0";
  const params = [];
  if (category){ sql += " AND category = ?"; params.push(category); }
  if (location){ sql += " AND location = ?"; params.push(location); }
  if (q){ sql += " AND (name LIKE ? OR sku LIKE ?)"; params.push('%'+q+'%', '%'+q+'%'); }
  db.all(sql, params, (err, rows) => {
    if (err) return res.status(500).json({error: err.message});
    res.json(rows);
  });
});

app.get('/api/v1/inventory/:id', (req, res) => {
  db.get("SELECT * FROM inventory WHERE id = ? AND deleted=0", [req.params.id], (err,row)=>{
    if (err) return res.status(500).json({error: err.message});
    if (!row) return res.status(404).json({error:'Not found'});
    res.json(row);
  });
});

app.post('/api/v1/inventory', (req, res) => {
  const { name, sku, category, quantity, unit_price, supplier_id, location, min_stock, notes } = req.body;
  if (!name || !sku) return res.status(400).json({error:'name and sku required'});
  const now = new Date().toISOString();
  db.run(`INSERT INTO inventory (name, sku, category, quantity, unit_price, supplier_id, location, min_stock, notes, created_at, updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
    [name, sku, category, quantity||0, unit_price||0, supplier_id||null, location||'', min_stock||0, notes||'', now, now],
    function(err){
      if (err){
        if (err.message.includes('UNIQUE')) return res.status(409).json({code:'SKU_DUPLICATE', message:'SKU already exists', details:{sku}});
        return res.status(500).json({error: err.message});
      }
      const id = this.lastID;
      db.get("SELECT * FROM inventory WHERE id=?", [id], (e,row)=>{
        audit(id, 'CREATE', null, row, req.body.reason || null);
        res.status(201).json(row);
      });
    });
});

app.put('/api/v1/inventory/:id', (req,res)=>{
  const id = req.params.id;
  db.get("SELECT * FROM inventory WHERE id = ?", [id], (err, before)=>{
    if (err) return res.status(500).json({error: err.message});
    if (!before) return res.status(404).json({error:'Not found'});
    const fields = ['name','sku','category','quantity','unit_price','supplier_id','location','min_stock','notes','deleted'];
    const updates = [];
    const params = [];
    fields.forEach(f=>{
      if (req.body[f] !== undefined){
        updates.push(f + " = ?");
        params.push(req.body[f]);
      }
    });
    if (updates.length === 0) return res.status(400).json({error:'No updatable fields provided'});
    params.push(new Date().toISOString());
    params.push(id);
    const sql = `UPDATE inventory SET ${updates.join(', ')}, updated_at = ? WHERE id = ?`;
    db.run(sql, params, function(err2){
      if (err2) return res.status(500).json({error: err2.message});
      db.get("SELECT * FROM inventory WHERE id = ?", [id], (err3, after)=>{
        audit(id, 'UPDATE', before, after, req.body.reason || null);
        res.json(after);
      });
    });
  });
});

app.patch('/api/v1/inventory/:id/quantity', (req,res)=>{
  const id = req.params.id;
  const delta = Number(req.body.delta || 0);
  const reason = req.body.reason || null;
  db.get("SELECT * FROM inventory WHERE id = ?", [id], (err, row)=>{
    if (err) return res.status(500).json({error: err.message});
    if (!row) return res.status(404).json({error:'Not found'});
    const before = row;
    const newQty = row.quantity + delta;
    db.run("UPDATE inventory SET quantity = ?, updated_at = ? WHERE id = ?", [newQty, new Date().toISOString(), id], function(err2){
      if (err2) return res.status(500).json({error: err2.message});
      db.get("SELECT * FROM inventory WHERE id = ?", [id], (err3, after)=>{
        audit(id, 'QTY_ADJUST', before, after, reason);
        res.json(after);
      });
    });
  });
});

app.delete('/api/v1/inventory/:id', (req,res)=>{
  const id = req.params.id;
  const mode = req.query.mode || 'soft'; // soft or hard
  db.get("SELECT * FROM inventory WHERE id = ?", [id], (err,row)=>{
    if (err) return res.status(500).json({error: err.message});
    if (!row) return res.status(404).json({error:'Not found'});
    const before = row;
    if (mode === 'hard'){
      db.run("DELETE FROM inventory WHERE id = ?", [id], function(err2){
        if (err2) return res.status(500).json({error: err2.message});
        audit(id, 'DELETE', before, null, req.body.reason || null);
        res.json({deleted: true});
      });
    } else {
      db.run("UPDATE inventory SET deleted=1, updated_at=? WHERE id = ?", [new Date().toISOString(), id], function(err2){
        if (err2) return res.status(500).json({error: err2.message});
        audit(id, 'SOFT_DELETE', before, null, req.body.reason || null);
        res.json({deleted: true});
      });
    }
  });
});

// Audit log endpoint (read-only)
app.get('/api/v1/audit', (req,res)=>{
  db.all("SELECT * FROM inventory_audit ORDER BY performed_at DESC LIMIT 200", [], (err, rows)=>{
    if (err) return res.status(500).json({error: err.message});
    res.json(rows);
  });
});

// Serve client app
app.get('/', (req,res) => {
  res.sendFile(path.join(__dirname, 'client', 'index.html'));
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, ()=> console.log(`Server running on port ${PORT}`));
