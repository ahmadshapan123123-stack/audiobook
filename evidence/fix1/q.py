import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
name = sys.argv[1]
con = sqlite3.connect('evidence/fix1/' + name)
con.row_factory = sqlite3.Row
rows = con.execute("""
SELECT b.title, p.status, p.currentPositionMs
FROM listening_progress p JOIN editions e ON p.editionId = e.id
JOIN books b ON e.bookId = b.id
ORDER BY b.title
""")
for r in rows:
    print(dict(r))
print('---audio---')
for r in con.execute('SELECT title, durationMs FROM audio_files ORDER BY title'):
    print(dict(r))