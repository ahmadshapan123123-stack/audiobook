import shutil, sqlite3, sys, uuid
sys.stdout.reconfigure(encoding='utf-8')

SRC = 'evidence/fix1/A0b'   # wal-aware snapshot with Ahmed present
DST = 'evidence/fix1/seed_c2.db'
for f in ('seed_c2.db', 'seed_c2.db-wal', 'seed_c2.db-shm'):
    try: import os; os.remove(os.path.join('evidence/fix1', f))
    except OSError: pass

shutil.copyfile(SRC, DST)
con = sqlite3.connect(DST)
con.row_factory = sqlite3.Row

ahmed = con.execute("SELECT id FROM authors WHERE name=?", ('أحمد خالد توفيق',)).fetchone()['id']
safari = con.execute("SELECT id FROM books WHERE title=?", ('سافاري الجديد',)).fetchone()['id']
mustaqbal = con.execute("SELECT id FROM books WHERE title=?", ('ملف المستقبل',)).fetchone()['id']

sid = str(uuid.uuid4())
cid = str(uuid.uuid4())
con.execute("INSERT INTO series (id, authorId, name, colorTheme, imagePath, description, isDemo) VALUES (?,?,?,?,?,?,1)",
            (sid, ahmed, 'سلسلة سافاري', None, None, None))
con.execute("UPDATE books SET seriesId=?, orderInSeries=1 WHERE id=?", (sid, safari))
con.execute("INSERT INTO collections (id, name, icon, remoteId, syncStatus, isDemo) VALUES (?,?,?,?,?,1)",
            (cid, 'مجموعة لاحقاً', None, None, 'SYNCED'))
con.execute("INSERT INTO collection_book_cross_ref (collectionId, bookId) VALUES (?,?)", (cid, safari))
con.execute("INSERT INTO collection_book_cross_ref (collectionId, bookId) VALUES (?,?)", (cid, mustaqbal))
con.commit()

print('series:', [(r['id'][:8], r['name']) for r in con.execute('SELECT * FROM series')])
print('books w/ series:', [r['title'] for r in con.execute("SELECT title FROM books WHERE seriesId IS NOT NULL")])
print('collections:', [(r['id'][:8], r['name']) for r in con.execute('SELECT * FROM collections')])
print('xref:', [tuple(r[:2]) for r in con.execute('SELECT collectionId, bookId FROM collection_book_cross_ref')])
con.close()