import sqlite3, sys, inspect
sys.stdout.reconfigure(encoding='utf-8')
a = sys.argv[1]; b = sys.argv[2]
con = sqlite3.connect('evidence/fix1/' + a)
cols = {r[0]: [c[1] for c in con.execute('PRAGMA table_info(' + r[0] + ')')]
        for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
print('editions cols:', cols['editions'])
print('audio_files cols:', cols['audio_files'])
for name in [a, b]:
    con = sqlite3.connect('evidence/fix1/' + name)
    con.row_factory = sqlite3.Row
    print('====', name, '====')
    sel = ','.join(cols['books'])
    for r in con.execute('SELECT ' + sel + ' FROM books ORDER BY title'):
        print(' book:', dict(r))
    seld = ','.join(cols['editions'])
    for r in con.execute('SELECT ' + seld + ' FROM editions ORDER BY id'):
        print(' ed :', dict(r))
    sela = ','.join(cols['audio_files'])
    for r in con.execute('SELECT ' + sela + ' FROM audio_files ORDER BY id'):
        print(' aud:', dict(r))