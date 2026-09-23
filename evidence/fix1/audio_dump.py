import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect('evidence/fix1/cleanup1')
con.row_factory = sqlite3.Row
for r in con.execute("SELECT * FROM audio_files"):
    print(dict(r))