import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect('evidence/fix1/A0b')
for r in con.execute("SELECT name, sql FROM sqlite_master WHERE type='table' AND name IN ('collection_book_cross_ref','favorite_books','chapters')"):
    print('###', r[0])
    print(r[1])
    print()
for r in con.execute("SELECT id, name, isDemo FROM authors"):
    print('AUTHOR', r[0], r[1], r[2])
for r in con.execute("SELECT id, title, seriesId FROM books"):
    print('BOOK', r[0][:8], r[1], r[2])