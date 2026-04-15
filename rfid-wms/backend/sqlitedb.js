/**
 * sqlitedb.js — SQLite 适配层
 * 
 * 开发环境: 使用 better-sqlite3 (高性能 C++ 模块)
 * 打包 exe:  使用 sql.js (纯 JS WASM，可被 pkg 打包)
 * 
 * 切换方式: 设置环境变量 PKG_EXE=1 即使用 sql.js
 * 
 * 对外暴露完全相同的 API: db.exec / db.prepare / db.transaction
 *                     stmt.run / stmt.get / stmt.all
 */

const fs = require('fs');
const path = require('path');

function createBetterSQLite(dbPath) {
  const Database = require('better-sqlite3');
  const db = new Database(dbPath);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  return db; // better-sqlite3 自身 API 已符合需求
}

async function createSqlJs(dbPath) {
  const initSqlJs = require('sql.js');
  const SQL = await initSqlJs(); // sql.js 需要异步初始化

  // 读取已有数据库文件或创建空数据库
  let buffer = null;
  if (fs.existsSync(dbPath)) {
    buffer = fs.readFileSync(dbPath);
  }

  let db;
  if (buffer !== null) {
    db = new SQL.Database(buffer);
  } else {
    db = new SQL.Database();
  }

  // 包装为 better-sqlite3 兼容的 API
  db.exec = function(sql) {
    // sql.js 的 run 支持多语句
    db.run(sql);
  };

  db.prepare = function(sql) {
    return {
      _db: db,
      _sql: sql,

      run(...params) {
        try {
          const stmt = db.prepare(this._sql);
          stmt.bind(params);
          // 逐行消费结果（必须调用 step 否则 stmt 不会执行）
          while (stmt.step()) { /* 消费结果 */ }
          stmt.free();
          const changes = db.getRowsModified();
          return { changes, lastInsertRowid: 0 };
        } catch (e) {
          throw e;
        }
      },

      get(...params) {
        const stmt = db.prepare(this._sql);
        stmt.bind(params);
        let result = null;
        if (stmt.step()) {
          result = stmt.getAsObject();
        }
        stmt.free();
        return result;
      },

      all(...params) {
        const stmt = db.prepare(this._sql);
        stmt.bind(params);
        const results = [];
        while (stmt.step()) {
          results.push(stmt.getAsObject());
        }
        stmt.free();
        return results;
      }
    };
  };

  db.transaction = function(fn) {
    return function(...args) {
      db.run('BEGIN');
      try {
        const result = fn(...args);
        db.run('COMMIT');
        return result;
      } catch (e) {
        try { db.run('ROLLBACK'); } catch(e2) {}
        throw e;
      }
    };
  };

  // 定期保存到磁盘（每 5 秒）
  db._dbPath = dbPath;
  db._saveInterval = setInterval(() => {
    try {
      const data = db.export();
      const buf = Buffer.from(data);
      fs.writeFileSync(dbPath, buf);
    } catch (e) {
      console.error('自动保存数据库失败:', e.message);
    }
  }, 5000);

  // 覆盖原始 close 方法
  db._origClose = db.close;
  db.close = function() {
    clearInterval(db._saveInterval);
    try {
      const data = db.export();
      const buf = Buffer.from(data);
      fs.writeFileSync(dbPath, buf);
    } catch (e) { /* 忽略 */ }
    db._origClose.call(db);
  };

  return db;
}

/**
 * 创建数据库连接（同步版本）
 * 注意：sql.js 模式实际上是异步的，但由于 server.js 的使用方式，
 * 我们在顶层 await 不可用的情况下，使用 IIFE 启动方式处理。
 * 
 * @param {string} dbPath - 数据库文件路径
 * @returns {Promise<object>} 兼容 better-sqlite3 API 的数据库实例
 */
async function createDatabaseAsync(dbPath) {
  // 确保目录存在
  const dir = path.dirname(dbPath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  // 如果是 pkg 打包环境，使用 sql.js
  if (process.env.PKG_EXE === '1' || process.pkg) {
    console.log('📦 使用 sql.js (WASM) 模式');
    return createSqlJs(dbPath);
  }

  // 开发环境使用 better-sqlite3
  try {
    console.log('⚡ 使用 better-sqlite3 模式');
    return createBetterSQLite(dbPath);
  } catch (e) {
    console.warn('⚠️ better-sqlite3 加载失败，回退到 sql.js:', e.message);
    return createSqlJs(dbPath);
  }
}

/**
 * 同步创建数据库（仅用于 better-sqlite3 模式）
 */
function createDatabaseSync(dbPath) {
  const dir = path.dirname(dbPath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  try {
    console.log('⚡ 使用 better-sqlite3 模式');
    return createBetterSQLite(dbPath);
  } catch (e) {
    throw new Error('better-sqlite3 不可用: ' + e.message);
  }
}

module.exports = { createDatabaseAsync, createDatabaseSync };
