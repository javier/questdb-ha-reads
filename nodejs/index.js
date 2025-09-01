import { Client } from 'pg';

const HOSTS = ["localhost:8812", "localhost:8813", "localhost:8814"];
const USER = "admin";
const PASS = "quest";
const DB = "qdb";

const QUERY = "select value from (show parameters) where property_path IN ( 'replication.role', 'cairo.wal.temp.pending.rename.table.prefix') limit 1";

let client = null;
let hostIndex = 0;

function dsnForCurrentHost() {
  const [host, port] = HOSTS[hostIndex].split(":");
  return { host, port, user: USER, password: PASS, database: DB, connectionTimeoutMillis: 3000 };
}

async function connectWithRetry() {
  while (true) {
    const cfg = dsnForCurrentHost();
    client = new Client(cfg);

    // prevent crashes when server closes the socket
    client.on('error', err => {
      console.error(`Client error on ${cfg.host}:${cfg.port}: ${err.message}`);
      try { client.end(); } catch {}
      client = null;
    });

    try {
      console.log(`Connecting to ${cfg.host}:${cfg.port}`);
      await client.connect();
      return;
    } catch (e) {
      console.error(`Connect failed on ${cfg.host}:${cfg.port}: ${e.message}`);
      try { await client.end(); } catch {}
      client = null;
      hostIndex = (hostIndex + 1) % HOSTS.length;
      await new Promise(r => setTimeout(r, 2000));
    }
  }
}

async function runQuery(sql, params) {
  if (!client) await connectWithRetry();
  try {
    const res = await client.query(sql, params);
    return res.rows;
  } catch (e) {
    console.error(`Query error, reconnecting: ${e.message}`);
    try { await client.end(); } catch {}
    client = null;
    await connectWithRetry();
    const res = await client.query(sql, params);
    return res.rows;
  }
}

(async () => {
  for (let i = 1; i <= 250; i++) {
    const rows = await runQuery(QUERY);
    console.log(`Query ${String(i).padStart(3)} -> ${rows?.[0]?.value}`);
    await new Promise(r => setTimeout(r, 300));
  }
  try { await client?.end(); } catch {}
})();
