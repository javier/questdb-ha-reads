package main

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
)

const dsn = "postgres://admin:quest@localhost:8812,localhost:8813,localhost:8814/qdb?target_session_attrs=any&connect_timeout=3"
const query = "select value from (show parameters) where property_path = 'cairo.wal.temp.pending.rename.table.prefix'"

func connectWithRetry(ctx context.Context) *pgx.Conn {
	for {
		conn, err := pgx.Connect(ctx, dsn)
		if err == nil {
			fmt.Println("Connected")
			return conn
		}
		fmt.Println("Connection attempt failed:", err)
		time.Sleep(2 * time.Second)
	}
}

func runQuery(ctx context.Context, conn **pgx.Conn, q string) (string, error) {
	if *conn == nil || (*conn).IsClosed() {
		*conn = connectWithRetry(ctx)
	}
	var v string
	err := (*conn).QueryRow(ctx, q).Scan(&v)
	if err == nil {
		return v, nil
	}
	(*conn).Close(ctx)
	*conn = connectWithRetry(ctx)
	err = (*conn).QueryRow(ctx, q).Scan(&v)
	return v, err
}

func main() {
	ctx := context.Background()
	var conn *pgx.Conn
	for i := 1; i <= 250; i++ {
		v, _ := runQuery(ctx, &conn, query)
		fmt.Printf("Query %3d -> %s\n", i, v)
		time.Sleep(300 * time.Millisecond)
	}
	if conn != nil && !conn.IsClosed() {
		conn.Close(ctx)
	}
}
