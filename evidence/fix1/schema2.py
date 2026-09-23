import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect(sys.argv[1])
for r in con.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name IN ('books','editions','authors','audio_files','chapters','edition_match_decisions','listening_progress')"):
    print(r[0]); print('---')