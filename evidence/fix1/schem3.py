import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect('evidence/fix1/A0b')
for r in con.execute("SELECT name, sql FROM sqlite_master WHERE type='table' AND name IN ('series','collections','books','favorite')"):
    print('###', r[0])
    print(r[1])
    print()