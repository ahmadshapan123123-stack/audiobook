import sqlite3, sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

def main():
    if len(sys.argv) < 3:
        print("usage: query_db.py <db> <sql>")
        return
    con = sqlite3.connect(sys.argv[1])
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    for sql in sys.argv[2:]:
        try:
            cur.execute(sql)
            rows = cur.fetchall()
            if cur.description is None:
                print("OK")
                continue
            print("%d row(s):" % len(rows))
            for r in rows:
                print(tuple(r))
        except Exception as e:
            print("ERROR:", e)
    con.close()

if __name__ == "__main__":
    main()