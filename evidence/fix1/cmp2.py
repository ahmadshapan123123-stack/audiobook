import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')

def dump(p):
    con = sqlite3.connect(p)
    con.row_factory = sqlite3.Row
    out = {}
    out['books'] = [(r['title'], r['authorId'][:8]) for r in con.execute('SELECT * FROM books ORDER BY title')]
    out['editions'] = [(r['id'][:8], r['bookId'][:8]) for r in con.execute('SELECT * FROM editions')]
    out['audio'] = [(r['id'][:8], r['editionId'][:8]) for r in con.execute('SELECT * FROM audio_files')]
    out['authors'] = [(r['id'][:8], r['name']) for r in con.execute('SELECT * FROM authors ORDER BY name')]
    con.close()
    return out

pre = dump('evidence/fix1/c2a_pre2.db')
post = dump('evidence/fix1/c2a_del1.db')
for k in ['books', 'editions', 'audio', 'authors']:
    print('===', k, '===')
    print(' pre :', pre[k])
    print(' post:', post[k])