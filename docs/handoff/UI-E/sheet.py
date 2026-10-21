import sys, os
from PIL import Image
out=sys.argv[1]; d=sys.argv[2]; files=sys.argv[3:]
ims=[Image.open(os.path.join(d,f)).convert('RGB') for f in files]
w=960; ims=[i.resize((w, int(i.size[1]*w/i.size[0]))) for i in ims]
cols=2; rows=(len(ims)+1)//2
H=max(i.size[1] for i in ims)
s=Image.new('RGB',(w*cols,H*rows))
for k,i in enumerate(ims): s.paste(i,((k%cols)*w,(k//cols)*H))
s.save(out)
