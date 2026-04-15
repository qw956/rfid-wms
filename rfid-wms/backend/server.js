/**
 * server.js — RFID 仓储管理系统后端
 * 
 * 支持通过 config.ini 配置端口
 * 启动后自动显示局域网 IP 供 PDA 连接
 */

const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const xlsx = require('xlsx');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const os = require('os');
const http = require('http');
const WebSocket = require('ws');

// ==================== 加载配置 ====================

function loadConfig() {
  const configPath = path.join(__dirname, 'config.ini');
  const config = { port: 3000 };
  try {
    if (fs.existsSync(configPath)) {
      const content = fs.readFileSync(configPath, 'utf-8');
      for (const line of content.split('\n')) {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
          const eqIndex = trimmed.indexOf('=');
          const key = trimmed.slice(0, eqIndex).trim().toLowerCase();
          const val = trimmed.slice(eqIndex + 1).trim();
          if (key === 'port') config.port = parseInt(val) || 3000;
        }
      }
    }
  } catch (e) { /* 使用默认配置 */ }
  return config;
}

// 自动获取局域网 IP
function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

const config = loadConfig();
const LOCAL_IP = getLocalIP();

// ==================== Express 服务器 ====================

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
const upload = multer({ dest: 'uploads/' });

// 中间件
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// 静态文件：pkg 打包时用 exe 所在目录，开发时用项目根目录
const WEB_DIR = process.pkg
  ? path.join(path.dirname(process.execPath), 'web')
  : path.join(__dirname, '..');
app.use(express.static(WEB_DIR));

// ==================== SQLite 数据库 ====================
// 
// pkg 打包后，native 模块（better-sqlite3）需要从 exe 旁的 node_modules 加载
// process.pkg !== undefined 表示运行在 pkg 打包的 exe 中

const DB_DIR = path.join(__dirname, 'data');
if (!fs.existsSync(DB_DIR)) {
  fs.mkdirSync(DB_DIR, { recursive: true });
}

// 设置 native 模块搜索路径
if (process.pkg) {
  const exeDir = path.dirname(process.execPath);
  module.paths.unshift(path.join(exeDir, 'node_modules'));
}

const Database = require('better-sqlite3');
const db = new Database(path.join(DB_DIR, 'rfid-wms.db'));

// 建表
db.exec(`
  CREATE TABLE IF NOT EXISTS tags (
    epc       TEXT PRIMARY KEY,
    tid       TEXT DEFAULT '',
    rssi      INTEGER DEFAULT 0,
    name      TEXT DEFAULT '未命名标签',
    category  TEXT DEFAULT '未分类',
    qty       INTEGER DEFAULT 1,
    location  TEXT DEFAULT '未知位置',
    department TEXT DEFAULT '',
    user_name  TEXT DEFAULT '',
    purchase_date TEXT DEFAULT '',
    user_data   TEXT DEFAULT '',
    scanCount INTEGER DEFAULT 1,
    created   TEXT NOT NULL,
    updated   TEXT NOT NULL,
    lastScan  TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS operation_logs (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    action    TEXT NOT NULL,
    detail    TEXT DEFAULT '',
    ip        TEXT DEFAULT '',
    created   TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS scan_sessions (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    epc       TEXT NOT NULL,
    tid       TEXT DEFAULT '',
    rssi      INTEGER DEFAULT 0,
    name      TEXT DEFAULT '',
    category  TEXT DEFAULT '',
    qty       INTEGER DEFAULT 1,
    location  TEXT DEFAULT '',
    department TEXT DEFAULT '',
    user_name  TEXT DEFAULT '',
    purchase_date TEXT DEFAULT '',
    user_data    TEXT DEFAULT '',
    scanned_at TEXT NOT NULL,
    UNIQUE(session_id, epc)
  );

  CREATE TABLE IF NOT EXISTS categories (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL UNIQUE,
    sort_order INTEGER DEFAULT 0,
    created   TEXT NOT NULL
  );
`);

// 兼容旧数据库：自动添加新字段
try { db.prepare('ALTER TABLE tags ADD COLUMN department TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE tags ADD COLUMN user_name TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE tags ADD COLUMN purchase_date TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE tags ADD COLUMN user_data TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE scan_sessions ADD COLUMN department TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE scan_sessions ADD COLUMN user_name TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE scan_sessions ADD COLUMN purchase_date TEXT DEFAULT \'\'').run(); } catch(e) {}
try { db.prepare('ALTER TABLE scan_sessions ADD COLUMN user_data TEXT DEFAULT \'\'').run(); } catch(e) {}

// 初始化默认类别（只在表为空时插入）
const catCount = db.prepare('SELECT COUNT(*) as c FROM categories').get().c;
if (catCount === 0) {
  const defaults = ['紧固件', '电子元器件', '润滑油脂', '密封件', '变频器', '其他'];
  defaults.forEach((name, i) => {
    db.prepare('INSERT INTO categories (name, sort_order, created) VALUES (?, ?, ?)').run(name, i, new Date().toISOString());
  });
}

console.log('✅ SQLite 数据库已连接: data/rfid-wms.db');

// 日志目录
const LOG_DIR = path.join(__dirname, 'logs');
if (!fs.existsSync(LOG_DIR)) {
  fs.mkdirSync(LOG_DIR, { recursive: true });
}

// 扫描文件目录（按时间命名保存）
const SCAN_FILES_DIR = path.join(__dirname, 'scan_files');
if (!fs.existsSync(SCAN_FILES_DIR)) {
  fs.mkdirSync(SCAN_FILES_DIR, { recursive: true });
}

// 导出目录
const EXPORT_DIR = path.join(__dirname, 'exports');
if (!fs.existsSync(EXPORT_DIR)) {
  fs.mkdirSync(EXPORT_DIR, { recursive: true });
}

// ==================== WebSocket ====================

// 当前活动扫描会话 ID（每次扫描开始时生成）
let activeSessionId = null;

// 存储所有 WebSocket 连接（区分类型：web / pda）
const wsClients = new Map(); // ws -> { type, id }

// 获取当前所有在线 PDA 设备列表
function getOnlinePdas() {
  const pdas = [];
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      const info = wsClients.get(client);
      if (info?.type === 'pda') pdas.push({ id: info.id, label: info.label || info.id });
    }
  });
  return pdas;
}

// 广播在线 PDA 设备列表给所有网页端
function broadcastPdaList() {
  const pdas = getOnlinePdas();
  broadcast('pda_list', { pdas });
}

// 广播消息给所有已连接的 WebSocket 客户端
function broadcast(event, data) {
  const msg = JSON.stringify({ event, data, ts: new Date().toISOString() });
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(msg);
    }
  });
}

wss.on('connection', (ws) => {
  console.log('🔌 WebSocket 客户端已连接，当前连接数:', wss.clients.size);
  // 发送当前活动 session
  ws.send(JSON.stringify({ event: 'connected', data: { sessionId: activeSessionId }, ts: new Date().toISOString() }));

  ws.on('message', (raw) => {
    try {
      const msg = JSON.parse(raw.toString());
      // 客户端注册身份
      if (msg.type === 'register') {
        wsClients.set(ws, {
          type: msg.clientType || 'web',
          id: msg.clientId || '',
          label: msg.clientLabel || msg.clientId || ''
        });
        console.log(`🔌 客户端注册: ${msg.clientType || 'web'} (${msg.clientLabel || msg.clientId || 'unknown'}), 当前 PDA 数: ${getOnlinePdas().length}`);
        // 通知 PDA 注册成功
        ws.send(JSON.stringify({ event: 'registered', data: { type: msg.clientType }, ts: new Date().toISOString() }));
        // 广播更新在线设备列表
        broadcastPdaList();
      }
    } catch (e) {
      console.warn('WebSocket 消息解析失败:', e.message);
    }
  });

  ws.on('close', () => {
    const info = wsClients.get(ws);
    if (info) {
      console.log(`🔌 ${info.type} (${info.label || info.id}) 断开连接`);
      wsClients.delete(ws);
      // PDA 断开时广播设备列表更新
      if (info.type === 'pda') broadcastPdaList();
    }
    console.log('🔌 当前连接数:', wss.clients.size);
  });
});

// 辅助函数：写入日志
function writeLog(action, data, ip) {
  try {
    // 写文件日志
    const logFile = path.join(LOG_DIR, `${new Date().toISOString().split('T')[0]}.log`);
    const logEntry = `[${new Date().toISOString()}] ${action}: ${JSON.stringify(data)}\n`;
    fs.appendFileSync(logFile, logEntry);

    // 写数据库日志（最多保留 1000 条）
    db.prepare(`
      INSERT INTO operation_logs (action, detail, ip, created)
      VALUES (?, ?, ?, ?)
    `).run(action, JSON.stringify(data), ip || '', new Date().toISOString());
    db.prepare('DELETE FROM operation_logs WHERE id NOT IN (SELECT id FROM operation_logs ORDER BY id DESC LIMIT 1000)').run();
  } catch (e) {
    // 日志写入失败不影响主流程
  }
}

// ==================== 标签管理 API ====================

/**
 * 扫描上传标签（去重 UPSERT）
 * POST /api/tags/scan
 */
app.post('/api/tags/scan', (req, res) => {
  const { epc, tid, rssi, session_id, user_data } = req.body;
  
  if (!epc) {
    return res.json({ success: false, message: 'EPC 不能为空' });
  }

  const now = new Date().toISOString();
  const ip = req.ip || req.connection.remoteAddress;
  
  // 如果没有活动会话，自动创建一个（PDA 首次扫描时触发）
  if (!activeSessionId) {
    activeSessionId = 'sess_' + new Date().toISOString().replace(/[:.]/g, '').slice(0, 17) + '_' + Math.random().toString(36).slice(2, 6);
    broadcast('session_started', { sessionId: activeSessionId });
    console.log('🆕 自动创建扫描会话:', activeSessionId);
  }
  const sid = session_id || activeSessionId;

  const existing = db.prepare('SELECT * FROM tags WHERE epc = ?').get(epc);
  
  if (existing) {
    // 更新现有标签（只更新扫描相关字段，不覆盖任何结构化字段）
    // user_data 仅作为原始数据存储，不再自动解析覆盖 name/category 等字段
    db.prepare(`
      UPDATE tags SET 
        tid = COALESCE(NULLIF(?, ''), tid),
        rssi = ?,
        lastScan = ?,
        scanCount = scanCount + 1,
        user_data = COALESCE(NULLIF(?, ''), user_data)
      WHERE epc = ?
    `).run(
      tid || '', rssi || existing.rssi, now, user_data || '',
      epc
    );
    
    writeLog('TAG_SCAN', { epc, action: 'updated', rssi }, ip);
    const tag = db.prepare('SELECT * FROM tags WHERE epc = ?').get(epc);

    // 同步写入扫描会话（去重，重复扫描时用最新标签信息更新）
    if (sid) {
      try {
        db.prepare(`
          INSERT INTO scan_sessions (session_id, epc, tid, rssi, name, category, qty, location, department, user_name, purchase_date, user_data, scanned_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(session_id, epc) DO UPDATE SET
            tid = excluded.tid, rssi = excluded.rssi,
            name = excluded.name, category = excluded.category,
            qty = excluded.qty, location = excluded.location,
            department = excluded.department, user_name = excluded.user_name,
            purchase_date = excluded.purchase_date,
            user_data = excluded.user_data,
            scanned_at = excluded.scanned_at
        `).run(sid, epc, tid || '', rssi || 0, tag.name, tag.category, tag.qty, tag.location, tag.department || '', tag.user_name || '', tag.purchase_date || '', tag.user_data || '', now);
      } catch(e) {}
    }

    // WebSocket 广播
    broadcast('tag_scanned', { tag, isNew: false, sessionId: sid });

    res.json({ success: true, tag, isNew: false });
  } else {
    // 新增标签（使用默认值，不从芯片数据自动解析）
    db.prepare(`
      INSERT INTO tags (epc, tid, rssi, name, category, qty, location, department, user_name, purchase_date, user_data, scanCount, created, updated, lastScan)
      VALUES (?, ?, ?, '未命名标签', '未分类', 1, '未知位置', '', '', '', ?, 1, ?, ?, ?)
    `).run(epc, tid || '', rssi || 0, user_data || '', now, now, now);
    
    writeLog('TAG_SCAN', { epc, action: 'created', rssi }, ip);
    const tag = db.prepare('SELECT * FROM tags WHERE epc = ?').get(epc);

    // 同步写入扫描会话（去重）
    if (sid) {
      try {
        db.prepare(`
          INSERT INTO scan_sessions (session_id, epc, tid, rssi, name, category, qty, location, department, user_name, purchase_date, user_data, scanned_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(session_id, epc) DO UPDATE SET
            tid = excluded.tid, rssi = excluded.rssi,
            name = excluded.name, category = excluded.category,
            qty = excluded.qty, location = excluded.location,
            department = excluded.department, user_name = excluded.user_name,
            purchase_date = excluded.purchase_date,
            user_data = excluded.user_data,
            scanned_at = excluded.scanned_at
        `).run(sid, epc, tid || '', rssi || 0, tag.name, tag.category, tag.qty, tag.location, tag.department || '', tag.user_name || '', tag.purchase_date || '', tag.user_data || '', now);
      } catch(e) {}
    }

    // WebSocket 广播
    broadcast('tag_scanned', { tag, isNew: true, sessionId: sid });

    res.json({ success: true, tag, isNew: true });
  }
});

/**
 * 获取所有标签
 * GET /api/tags
 */
app.get('/api/tags', (req, res) => {
  const { search, category } = req.query;
  
  let sql = 'SELECT * FROM tags';
  const params = [];
  const conditions = [];
  
  if (search) {
    conditions.push('(epc LIKE ? OR name LIKE ? OR location LIKE ? OR department LIKE ? OR user_name LIKE ?)');
    const s = `%${search}%`;
    params.push(s, s, s, s, s);
  }
  
  if (category && category !== 'all') {
    conditions.push('category = ?');
    params.push(category);
  }
  
  if (conditions.length > 0) {
    sql += ' WHERE ' + conditions.join(' AND ');
  }
  
  sql += ' ORDER BY lastScan DESC';
  
  const tags = db.prepare(sql).all(...params);
  res.json({ success: true, tags });
});

/**
 * 导出 Excel（全量标签）
 * GET /api/tags/export
 * ⚠️ 必须放在 /api/tags/:epc 之前
 */
app.get('/api/tags/export', (req, res) => {
  try {
    const tags = db.prepare('SELECT * FROM tags ORDER BY lastScan DESC').all();
    
    const workbook = xlsx.utils.book_new();
    const worksheetData = [
      ['序号', 'RFID 标签', '名称', '类别', '数量', '存放位置', '使用部门', '使用人', '入库/采购时间', '扫描时间']
    ];
    
    tags.forEach((tag, index) => {
      worksheetData.push([
        index + 1, tag.epc, tag.name || '', tag.category || '', tag.qty || 0, tag.location || '',
        tag.department || '', tag.user_name || '', tag.purchase_date || '', tag.lastScan ? new Date(tag.lastScan).toLocaleString('zh-CN', {hour12:false}) : ''
      ]);
    });
    
    const worksheet = xlsx.utils.aoa_to_sheet(worksheetData);
    worksheet['!cols'] = [
      { wch: 6 }, { wch: 30 }, { wch: 20 }, { wch: 15 }, { wch: 8 }, { wch: 15 },
      { wch: 12 }, { wch: 12 }, { wch: 18 }, { wch: 22 }
    ];
    xlsx.utils.book_append_sheet(workbook, worksheet, '标签数据');
    
    const fileName = `RFID标签数据_${new Date().toISOString().replace(/[:.]/g, '-').slice(0,19)}.xlsx`;
    const filePath = path.join(EXPORT_DIR, fileName);
    xlsx.writeFile(workbook, filePath);
    
    writeLog('EXPORT', { fileName, count: tags.length });
    
    res.download(filePath, fileName, (err) => {
      if (err) console.error('下载失败:', err);
    });
  } catch (error) {
    console.error('导出 Excel 失败:', error);
    res.status(500).json({ success: false, message: '导出失败' });
  }
});

/**
 * 获取单个标签
 * GET /api/tags/:epc
 */
app.get('/api/tags/:epc', (req, res) => {
  const epc = decodeURIComponent(req.params.epc);
  const tag = db.prepare('SELECT * FROM tags WHERE epc = ?').get(epc);
  
  if (tag) {
    res.json({ success: true, tag });
  } else {
    res.json({ success: false, message: '标签不存在' });
  }
});

/**
 * 更新标签信息
 * PUT /api/tags/:epc
 */
app.put('/api/tags/:epc', (req, res) => {
  const epc = decodeURIComponent(req.params.epc);
  const { name, category, qty, location, department, user_name, purchase_date, user_data } = req.body;

  const existing = db.prepare('SELECT * FROM tags WHERE epc = ?').get(epc);

  if (!existing) {
    return res.json({ success: false, message: '标签不存在' });
  }

  db.prepare(`
    UPDATE tags SET
      name = COALESCE(NULLIF(?, ''), name),
      category = COALESCE(NULLIF(?, ''), category),
      qty = COALESCE(NULLIF(?, ''), qty),
      location = COALESCE(NULLIF(?, ''), location),
      department = COALESCE(NULLIF(?, ''), department),
      user_name = COALESCE(NULLIF(?, ''), user_name),
      purchase_date = COALESCE(NULLIF(?, ''), purchase_date),
      user_data = COALESCE(NULLIF(?, ''), user_data),
      updated = ?
    WHERE epc = ?
  `).run(
    name !== undefined ? name : null,
    category !== undefined ? category : null,
    qty !== undefined ? qty : null,
    location !== undefined ? location : null,
    department !== undefined ? department : null,
    user_name !== undefined ? user_name : null,
    purchase_date !== undefined ? purchase_date : null,
    user_data !== undefined ? user_data : null,
    new Date().toISOString(),
    epc
  );

  writeLog('TAG_UPDATE', { epc, name, category, qty, location, department, user_name });
  const tag = db.prepare('SELECT * FROM tags WHERE epc = ?').get(epc);

  // WebSocket 广播更新
  broadcast('tag_updated', { tag });

  res.json({ success: true, tag });
});

/**
 * 删除标签
 * DELETE /api/tags/:epc
 */
app.delete('/api/tags/:epc', (req, res) => {
  const epc = decodeURIComponent(req.params.epc);
  const result = db.prepare('DELETE FROM tags WHERE epc = ?').run(epc);
  
  if (result.changes > 0) {
    writeLog('TAG_DELETE', { epc });
    broadcast('tag_deleted', { epc });
    res.json({ success: true });
  } else {
    res.json({ success: false, message: '标签不存在' });
  }
});

/**
 * 批量删除标签
 * POST /api/tags/batch-delete
 * Body: { epcs: string[] }
 */
app.post('/api/tags/batch-delete', (req, res) => {
  const { epcs } = req.body;
  if (!Array.isArray(epcs) || epcs.length === 0) {
    return res.json({ success: false, message: '请选择要删除的标签' });
  }

  const ip = req.ip || req.connection.remoteAddress;
  let deleted = 0;
  const stmt = db.prepare('DELETE FROM tags WHERE epc = ?');
  const transaction = db.transaction((items) => {
    for (const epc of items) {
      const result = stmt.run(epc);
      if (result.changes > 0) {
        deleted++;
        writeLog('TAG_DELETE', { epc, action: 'batch' }, ip);
        broadcast('tag_deleted', { epc });
      }
    }
  });
  transaction(epcs);

  res.json({ success: true, deleted });
});

// ==================== 类别管理 API ====================

/**
 * 获取所有类别
 * GET /api/categories
 */
app.get('/api/categories', (req, res) => {
  const categories = db.prepare('SELECT * FROM categories ORDER BY sort_order ASC, id ASC').all();
  res.json({ success: true, categories });
});

/**
 * 新增类别
 * POST /api/categories
 */
app.post('/api/categories', (req, res) => {
  const { name } = req.body;
  if (!name || !name.trim()) {
    return res.json({ success: false, message: '类别名称不能为空' });
  }
  const trimmed = name.trim();
  try {
    db.prepare('INSERT INTO categories (name, sort_order, created) VALUES (?, ?, ?)').run(trimmed, 999, new Date().toISOString());
    const cat = db.prepare('SELECT * FROM categories WHERE name = ?').get(trimmed);
    writeLog('CATEGORY_ADD', { name: trimmed });
    res.json({ success: true, category: cat });
  } catch(e) {
    if (e.message.includes('UNIQUE')) {
      return res.json({ success: false, message: '该类别已存在' });
    }
    res.json({ success: false, message: '添加失败' });
  }
});

/**
 * 修改类别名称
 * PUT /api/categories/:id
 */
app.put('/api/categories/:id', (req, res) => {
  const id = parseInt(req.params.id);
  const { name } = req.body;
  if (!name || !name.trim()) {
    return res.json({ success: false, message: '类别名称不能为空' });
  }
  const trimmed = name.trim();
  const existing = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
  if (!existing) return res.json({ success: false, message: '类别不存在' });

  try {
    db.prepare('UPDATE categories SET name = ? WHERE id = ?').run(trimmed, id);
    // 同步更新 tags 表和 scan_sessions 表中的旧类别名
    db.prepare('UPDATE tags SET category = ? WHERE category = ?').run(trimmed, existing.name);
    db.prepare('UPDATE scan_sessions SET category = ? WHERE category = ?').run(trimmed, existing.name);
    writeLog('CATEGORY_UPDATE', { from: existing.name, to: trimmed });
    res.json({ success: true });
  } catch(e) {
    if (e.message.includes('UNIQUE')) {
      return res.json({ success: false, message: '该类别名称已存在' });
    }
    res.json({ success: false, message: '修改失败' });
  }
});

/**
 * 删除类别
 * DELETE /api/categories/:id
 */
app.delete('/api/categories/:id', (req, res) => {
  const id = parseInt(req.params.id);
  const cat = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
  if (!cat) return res.json({ success: false, message: '类别不存在' });

  // 把使用该类别的标签改为"未分类"
  db.prepare('UPDATE tags SET category = ? WHERE category = ?').run('未分类', cat.name);
  db.prepare('UPDATE scan_sessions SET category = ? WHERE category = ?').run('未分类', cat.name);
  db.prepare('DELETE FROM categories WHERE id = ?').run(id);
  writeLog('CATEGORY_DELETE', { name: cat.name });
  res.json({ success: true });
});

// ==================== 扫描会话 API ====================

/**
 * 开启新扫描会话
 * POST /api/sessions/start
 */
app.post('/api/sessions/start', (req, res) => {
  const sessionId = 'sess_' + new Date().toISOString().replace(/[:.]/g, '').slice(0, 17) + '_' + Math.random().toString(36).slice(2, 6);
  activeSessionId = sessionId;
  broadcast('session_started', { sessionId });
  res.json({ success: true, sessionId });
});

/**
 * 结束当前扫描会话
 * POST /api/sessions/stop
 */
app.post('/api/sessions/stop', (req, res) => {
  const sid = activeSessionId;
  activeSessionId = null;
  broadcast('session_stopped', { sessionId: sid });
  res.json({ success: true, sessionId: sid });
});

/**
 * 获取当前/指定会话中的扫描数据（临时）
 * GET /api/sessions/:sessionId/tags
 * GET /api/sessions/current/tags
 */
app.get('/api/sessions/:sessionId/tags', (req, res) => {
  const sid = req.params.sessionId === 'current' ? activeSessionId : req.params.sessionId;
  if (!sid) return res.json({ success: true, tags: [], sessionId: null });

  const tags = db.prepare('SELECT * FROM scan_sessions WHERE session_id = ? ORDER BY scanned_at ASC').all(sid);
  res.json({ success: true, tags, sessionId: sid });
});

/**
 * 获取当前活动 session ID
 * GET /api/sessions/active
 */
app.get('/api/sessions/active', (req, res) => {
  res.json({ success: true, sessionId: activeSessionId });
});

/**
 * 更新会话中某条记录的信息（name/category/qty/location）
 * PUT /api/sessions/:sessionId/tags/:epc
 */
app.put('/api/sessions/:sessionId/tags/:epc', (req, res) => {
  const sid = req.params.sessionId;
  const epc = decodeURIComponent(req.params.epc);
  const { name, category, qty, location, department, user_name, purchase_date, user_data } = req.body;

  db.prepare(`
    UPDATE scan_sessions SET
      name = COALESCE(NULLIF(?, ''), name),
      category = COALESCE(NULLIF(?, ''), category),
      qty = COALESCE(NULLIF(?, ''), qty),
      location = COALESCE(NULLIF(?, ''), location),
      department = COALESCE(NULLIF(?, ''), department),
      user_name = COALESCE(NULLIF(?, ''), user_name),
      purchase_date = COALESCE(NULLIF(?, ''), purchase_date),
      user_data = COALESCE(NULLIF(?, ''), user_data)
    WHERE session_id = ? AND epc = ?
  `).run(
    name !== undefined ? name : null,
    category !== undefined ? category : null,
    qty !== undefined ? qty : null,
    location !== undefined ? location : null,
    department !== undefined ? department : null,
    user_name !== undefined ? user_name : null,
    purchase_date !== undefined ? purchase_date : null,
    user_data !== undefined ? user_data : null,
    sid, epc
  );

  const tag = db.prepare('SELECT * FROM scan_sessions WHERE session_id = ? AND epc = ?').get(sid, epc);
  res.json({ success: true, tag });
});

/**
 * 将会话数据保存到正式标签库，并保存为时间命名的文件
 * POST /api/sessions/:sessionId/commit
 */
app.post('/api/sessions/:sessionId/commit', (req, res) => {
  const sid = req.params.sessionId;
  const tags = db.prepare('SELECT * FROM scan_sessions WHERE session_id = ?').all(sid);

  if (tags.length === 0) {
    return res.json({ success: false, message: '会话中没有数据' });
  }

  const now = new Date().toISOString();
  let saved = 0;

  const commitTag = db.transaction((tagList) => {
    for (const t of tagList) {
      const existing = db.prepare('SELECT * FROM tags WHERE epc = ?').get(t.epc);
      if (existing) {
        db.prepare(`
          UPDATE tags SET
            name = CASE WHEN ? != '' THEN ? ELSE name END,
            category = CASE WHEN ? != '' THEN ? ELSE category END,
            qty = CASE WHEN ? > 0 THEN ? ELSE qty END,
            location = CASE WHEN ? != '' THEN ? ELSE location END,
            department = CASE WHEN ? != '' THEN ? ELSE department END,
            user_name = CASE WHEN ? != '' THEN ? ELSE user_name END,
            purchase_date = CASE WHEN ? != '' THEN ? ELSE purchase_date END,
            lastScan = ?,
            updated = ?
          WHERE epc = ?
        `).run(
          t.name, t.name, t.category, t.category,
          t.qty, t.qty, t.location, t.location,
          t.department || '', t.department || '', t.user_name || '', t.user_name || '',
          t.purchase_date || '', t.purchase_date || '',
          t.scanned_at, now, t.epc
        );
      } else {
        db.prepare(`
          INSERT INTO tags (epc, tid, rssi, name, category, qty, location, department, user_name, purchase_date, scanCount, created, updated, lastScan)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
        `).run(
          t.epc, t.tid || '', t.rssi || 0,
          t.name || '未命名标签', t.category || '未分类',
          t.qty || 1, t.location || '未知位置',
          t.department || '', t.user_name || '', t.purchase_date || '',
          t.scanned_at, now, t.scanned_at
        );
      }
      saved++;
    }
  });

  try {
    commitTag(tags);
  } catch (e) {
    return res.status(500).json({ success: false, message: '保存失败: ' + e.message });
  }

  // 保存为时间命名的 Excel 文件
  const dateStr = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const fileName = `扫描记录_${dateStr}.xlsx`;
  const filePath = path.join(SCAN_FILES_DIR, fileName);

  try {
    const workbook = xlsx.utils.book_new();
    const worksheetData = [
      ['序号', 'RFID 标签', '名称', '类别', '数量', '存放位置', '使用部门', '使用人', '入库/采购时间', '扫描时间']
    ];
    tags.forEach((t, i) => {
      worksheetData.push([
        i + 1, t.epc, t.name || '', t.category || '',
        t.qty, t.location || '', t.department || '', t.user_name || '', t.purchase_date || '',
        new Date(t.scanned_at).toLocaleString('zh-CN', {hour12:false})
      ]);
    });
    const ws = xlsx.utils.aoa_to_sheet(worksheetData);
    ws['!cols'] = [
      { wch: 6 }, { wch: 30 }, { wch: 20 }, { wch: 15 },
      { wch: 8 }, { wch: 15 }, { wch: 12 }, { wch: 12 }, { wch: 18 }, { wch: 22 }
    ];
    xlsx.utils.book_append_sheet(workbook, ws, '扫描记录');
    xlsx.writeFile(workbook, filePath);
  } catch (e) {
    console.error('保存扫描文件失败:', e);
  }

  writeLog('SESSION_COMMIT', { sessionId: sid, count: saved, file: fileName });
  broadcast('session_committed', { sessionId: sid, count: saved });

  res.json({ success: true, saved, fileName, downloadUrl: `/api/sessions/files/${fileName}` });
});

/**
 * 清空会话数据（不保存）
 * DELETE /api/sessions/:sessionId
 */
app.delete('/api/sessions/:sessionId', (req, res) => {
  const sid = req.params.sessionId;
  db.prepare('DELETE FROM scan_sessions WHERE session_id = ?').run(sid);
  if (activeSessionId === sid) activeSessionId = null;
  broadcast('session_cleared', { sessionId: sid });
  res.json({ success: true });
});

/**
 * 列出所有保存的扫描文件
 * GET /api/sessions/files
 */
app.get('/api/sessions/files', (req, res) => {
  try {
    const files = fs.readdirSync(SCAN_FILES_DIR)
      .filter(f => f.endsWith('.xlsx'))
      .map(f => {
        const stat = fs.statSync(path.join(SCAN_FILES_DIR, f));
        return { name: f, size: stat.size, created: stat.birthtime.toISOString() };
      })
      .sort((a, b) => new Date(b.created) - new Date(a.created));
    res.json({ success: true, files });
  } catch (e) {
    res.json({ success: true, files: [] });
  }
});

/**
 * 下载指定扫描文件
 * GET /api/sessions/files/:filename
 */
app.get('/api/sessions/files/:filename', (req, res) => {
  const filePath = path.join(SCAN_FILES_DIR, req.params.filename);
  if (fs.existsSync(filePath)) {
    res.download(filePath, req.params.filename);
  } else {
    res.status(404).json({ success: false, message: '文件不存在' });
  }
});

// ==================== 统计 API ====================

app.get('/api/stats', (req, res) => {
  const today = new Date().toISOString().split('T')[0];
  const totalTags = db.prepare('SELECT COUNT(*) as count FROM tags').get().count;
  const todayScans = db.prepare("SELECT COUNT(*) as count FROM tags WHERE lastScan LIKE ? || '%'").get(today).count;
  const categoryRows = db.prepare('SELECT category, COUNT(*) as count FROM tags GROUP BY category').all();
  const categoryStats = {};
  categoryRows.forEach(r => { categoryStats[r.category] = r.count; });
  res.json({ success: true, stats: { totalTags, todayScans, categoryStats } });
});

// ==================== 系统管理 API ====================

app.get('/api/logs', (req, res) => {
  const limit = parseInt(req.query.limit) || 50;
  const logs = db.prepare('SELECT * FROM operation_logs ORDER BY id DESC LIMIT ?').all(limit);
  res.json({ success: true, logs });
});

app.delete('/api/logs', (req, res) => {
  db.prepare('DELETE FROM operation_logs').run();
  res.json({ success: true });
});

const BASE_DIR = process.pkg ? path.dirname(process.execPath) : path.join(__dirname, '..');
const APK_PATH = path.join(BASE_DIR, 'android-app', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');

app.get('/api/system', (req, res) => {
  const dbFile = path.join(DB_DIR, 'rfid-wms.db');
  const dbSize = fs.existsSync(dbFile) ? (fs.statSync(dbFile).size / 1024).toFixed(1) : '0';
  const totalLogs = db.prepare('SELECT COUNT(*) as count FROM operation_logs').get().count;
  const actionStats = db.prepare('SELECT action, COUNT(*) as count FROM operation_logs GROUP BY action ORDER BY count DESC').all();
  const dailyScans = db.prepare(`
    SELECT DATE(created) as date, COUNT(*) as count
    FROM operation_logs
    WHERE action = 'TAG_SCAN' AND created >= datetime('now', '-7 days')
    GROUP BY DATE(created) ORDER BY date
  `).all();

  const apkExists = fs.existsSync(APK_PATH);
  const apkSize = apkExists ? (fs.statSync(APK_PATH).size / 1024 / 1024).toFixed(2) : '0';
  const apkMtime = apkExists ? fs.statSync(APK_PATH).mtime.toISOString() : null;

  // 扫描文件列表
  let scanFiles = [];
  try {
    scanFiles = fs.readdirSync(SCAN_FILES_DIR)
      .filter(f => f.endsWith('.xlsx'))
      .length;
  } catch(e) {}

  res.json({
    success: true,
    system: {
      dbSize: dbSize + ' KB', totalLogs, actionStats, dailyScans,
      scanFiles,
      apk: { exists: apkExists, size: apkSize + ' MB', updated: apkMtime }
    }
  });
});

// ==================== APP 下载 ====================

app.get('/download/apk', (req, res) => {
  if (fs.existsSync(APK_PATH)) {
    res.download(APK_PATH, 'RFID-WMS.apk', (err) => {
      if (err) console.error('APK 下载失败:', err);
    });
  } else {
    res.status(404).send('APK 文件不存在');
  }
});

// ==================== 启动服务器 ====================

const PORT = process.env.PORT || config.port;
server.listen(PORT, () => {
  console.log('');
  console.log('  ========================================');
  console.log('    RFID 仓储管理系统');
  console.log('  ========================================');
  console.log('');
  console.log('  本机访问:   http://localhost:' + PORT);
  console.log('  局域网访问: http://' + LOCAL_IP + ':' + PORT);
  console.log('  WebSocket:  ws://' + LOCAL_IP + ':' + PORT);
  console.log('');
  console.log('  PDA 设备请将服务器地址设置为:');
  console.log('  http://' + LOCAL_IP + ':' + PORT + '/api');
  console.log('');
  console.log('  修改端口请编辑 config.ini 文件');
  console.log('  按 Ctrl+C 停止服务器');
  console.log('');
  console.log('  ========================================');
});
