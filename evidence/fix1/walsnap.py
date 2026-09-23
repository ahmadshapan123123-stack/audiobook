import os, sqlite3, sys, subprocess

ADB = os.path.join(os.environ['LOCALAPPDATA'], 'Android', 'Sdk', 'platform-tools', 'adb.exe')
OUT = 'evidence/fix1'

def pull(dbname):
    base = os.path.join(OUT, dbname)
    for ext in ('', '-wal', '-shm'):
        dest = base + ext
        remote = 'databases/audiobook.db' + (ext if ext else '')
        with open(dest, 'wb') as f:
            r = subprocess.run([ADB, '-s', 'emulator-5554', 'exec-out', 'run-as',
                                'com.example.audiobook', 'cat', remote],
                               stdout=f, stderr=subprocess.DEVNULL)
        if r.returncode != 0:
            try:
                os.remove(dest)
            except OSError:
                pass
    return base

def view(dbname):
    base = pull(dbname)
    con = sqlite3.connect(base)
    con.row_factory = sqlite3.Row
    out = {}
    out['books'] = [(r['title'], r['authorId'][:8]) for r in con.execute('SELECT * FROM books ORDER BY title')]
    out['editions'] = con.execute('SELECT COUNT(*) FROM editions').fetchone()[0]
    out['audio'] = con.execute('SELECT COUNT(*) FROM audio_files').fetchone()[0]
    out['sound'] = con.execute('SELECT COUNT(*) FROM audio_files').fetchone()[0]
    out['authors'] = [(r['id'][:8], r['name']) for r in con.execute('SELECT * FROM authors ORDER BY name')]
    out['series'] = con.execute('SELECT COUNT(*) FROM series').fetchone()[0]
    out['collections'] = con.execute('SELECT COUNT(*) FROM collections').fetchone()[0]
    return out

if __name__ == '__main__':
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    which = sys.argv[1] if len(sys.argv) > 1 else 'snap'
    d = view(which)
    print('books:', d['books'])
    print('editions:', d['editions'])
    print('audio:', d['audio'])
    print('authors:', d['authors'])
    print('series:', d['series'], 'collections:', d['collections'])