import glob, re
for f in glob.glob('build/test-results/test/*.xml'):
    s = open(f).read()
    for m in re.finditer(r'<failure message="(.{0,1500})', s):
        print(f.split('/')[-1], m.group(1)[:1500])
