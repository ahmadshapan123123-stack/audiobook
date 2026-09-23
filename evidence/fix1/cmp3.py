import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')

def dump(p):
    con = sqlite3.connect(p)
    con.row_factory = sqlite3.Row
    out = {
        'books': [(r['title'], r['authorId'][:8]) for r in con.execute('SELECT * FROM books ORDER BY title')],
        'editions': [(r['id'][:8], r['bookId'][:8]) for r in con.execute('SELECT * FROM editions')],
        'audio': [(r['id'][:8], r['editionId'][:8]) for r in con.execute('SELECT * FROM audio_files')],
        'authors': [(r['id'][:8], r['name']) for r in con.execute('SELECT * FROM authors ORDER BY name')],
    }
    con.close()
    return out

base = dump('evidence/fix1/audiobook_beforeC1.db')
cur = dump('evidence/fix1/cur_now.db')
print('books base:', base['books'])
print('books cur :', cur['books'])
print('audio base:', base['audio'])
print('audio cur :', cur['audio'])
print('editions base:', base['editions'])
print('editions cur :', cur['editions'])