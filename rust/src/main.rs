use anyhow::Result;
use tokio::time::{sleep, Duration};
use tokio_postgres::{Config, NoTls};

const QUERY: &str = "select value from (show parameters) where property_path = 'cairo.wal.temp.pending.rename.table.prefix'";

async fn connect_with_retry() -> (tokio_postgres::Client, tokio_postgres::Connection<tokio_postgres::Socket, tokio_postgres::tls::NoTlsStream>) {
    loop {
        let mut cfg = Config::new();
        cfg.user("admin")
            .password("quest")
            .dbname("qdb")
            .host("localhost").port(8812)
            .host("localhost").port(8813)
            .host("localhost").port(8814);
        match cfg.connect(NoTls).await {
            Ok((client, conn)) => return (client, conn),
            Err(e) => {
                eprintln!("Connection attempt failed: {}", e);
                sleep(Duration::from_secs(2)).await;
            }
        }
    }
}

async fn run_query(client: &tokio_postgres::Client) -> Result<String> {
    let row = client.query_one(QUERY, &[]).await?;
    Ok(row.get::<_, String>(0))
}

#[tokio::main]
async fn main() -> Result<()> {
    let (mut client, mut connection) = connect_with_retry().await;
    tokio::spawn(async move { let _ = connection.await; });

    for i in 1..=250 {
        match run_query(&client).await {
            Ok(v) => println!("Query {:3} -> {}", i, v),
            Err(_) => {
                let (c, conn) = connect_with_retry().await;
                client = c;
                tokio::spawn(async move { let _ = conn.await; });
                let v = run_query(&client).await?;
                println!("Query {:3} -> {}", i, v);
            }
        }
        sleep(Duration::from_millis(300)).await;
    }
    Ok(())
}
