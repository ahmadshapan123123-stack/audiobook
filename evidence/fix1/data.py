import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect(sys.argv[1])
con.row_factory = sqlite3.Row
print('SERIES:')
for r in con.execute('SELECT s.id, s.name, COUNT(b.id) AS n FROM series s LEFT JOIN books b ON b.seriesId = s.id GROUP BY s.id'):
    print(' ', r['name'], '->', r['n'], 'books')
print('BOOKS:')
for r in con.execute('SELECT b.title, a.name as author, COALESCE(s.name, "(none)") as series FROM books b JOIN authors a ON a.id = b.authorId LEFT JOIN series s ON s.id = b.seriesId'):
    print(' ', r['title'], '|', r['author'], '|', r['series'])
print('FAVORITE:', [r['bookId'] for r in con.execute('SELECT * FROM favorite_books')])
print('COLLECTIONS:', [r['name'] for r in con.execute('SELECT * FROM collections')])
print('PROGRESS:', list(con.execute('SELECT editionId, status, currentPositionMs FROM listening_progress')))