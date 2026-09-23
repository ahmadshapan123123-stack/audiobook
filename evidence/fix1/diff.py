import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
def titles(p):
    con = sqlite3.connect(p); t = set(r[0] for r in con.execute('SELECT title FROM books')); con.close(); return t
pre = titles(sys.argv[1]); post = titles(sys.argv[2])
print('missing:', sorted(pre - post) or 'NONE')
print('extra:', sorted(post - pre) or 'NONE')