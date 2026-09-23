import xml.etree.ElementTree as ET, sys
sys.stdout.reconfigure(encoding='utf-8')
ET.register_namespace('android', '')
ns = {'a': 'http://schemas.android.com/apk/res/android'}
root = ET.parse(sys.argv[1]).getroot()
only_click = len(sys.argv) > 2 and sys.argv[2] == 'click'
for node in root.iter('node'):
    cl = node.get('clickable') == 'true'
    txt = node.get('text', '')
    cd = node.get('content-desc', '')
    if only_click and not cl:
        continue
    label = txt or cd
    if not label:
        continue
    cls = node.get('class', '').split('.')[-1]
    b = node.get('bounds', '')
    print(f'{"CLK " if cl else "    "} {cls:14} {label[:45]:45} {b}')