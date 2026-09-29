"""Generate source SVG lockups and pixel previews. Run from the repository root."""
from pathlib import Path
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent
ROOT.mkdir(exist_ok=True)
TERRA = '#A74731'
AMBER = '#9A5E16'
CORAL = '#B64F43'
CREAM = '#FFF8EA'
INK = '#312921'
SAGE = '#365847'

# 108-unit coordinate system matches the Android adaptive foreground.
HOUSE = 'M25 55 L54 31 L70 44 L70 31 L77 31 L77 50 L83 55 L83 85 L25 85 Z M19 55 L54 26 L89 55 L83 61 L54 37 L25 61 Z'
HEART = 'M76 12 C69 5 60 11 63 19 C65 25 72 29 76 32 C80 29 87 25 89 19 C92 11 83 5 76 12 Z'
SMOKE = 'M76 31 C75 36 77 38 74 42'

def mark(x=0, y=0, scale=1, fill=CREAM):
    return f'<g transform="translate({x} {y}) scale({scale})" fill="{fill}"><path d="{HOUSE}"/><path d="{HEART}"/><path d="{SMOKE}" fill="none" stroke="{fill}" stroke-width="3.2" stroke-linecap="round"/></g>'

def emblem(x=0, y=0, cell=512, fill=CREAM):
    return mark(x+cell*16/108, y+cell*17/108, cell*.7/108, fill)

def svg(name, body, width=512, height=512):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}">{body}</svg>\n', encoding='utf8')
    return path

def tile(color=TERRA):
    return f'<rect width="512" height="512" rx="110" fill="{color}"/>'

svg('logo/01-horizontal.svg', f'<rect width="960" height="320" fill="{CREAM}"/><rect x="36" y="40" width="240" height="240" rx="50" fill="{TERRA}"/>{emblem(36,40,240)}<text x="310" y="187" fill="{INK}" font-family="DejaVu Serif,serif" font-weight="bold" font-size="77">Вкус детства</text>', 960, 320)
svg('logo/02-stacked.svg', f'<rect width="512" height="512" fill="{CREAM}"/><rect x="140" y="50" width="232" height="232" rx="49" fill="{TERRA}"/>{emblem(140,50,232)}<text x="256" y="345" text-anchor="middle" fill="{INK}" font-family="DejaVu Serif,serif" font-weight="bold" font-size="50">Вкус детства</text><text x="256" y="387" text-anchor="middle" fill="{SAGE}" font-family="DejaVu Sans,sans-serif" font-size="18" letter-spacing="2">СЕМЕЙНЫЕ РЕЦЕПТЫ</text>')
svg('logo/03-mark.svg', tile()+emblem())
svg('logo/mark-mono.svg', f'<rect width="512" height="512" rx="110" fill="{INK}"/>'+emblem(fill='#FFFFFF'))
for name,color in [('terracotta',TERRA),('amber',AMBER),('coral',CORAL)]:
    svg(f'icon/{name}.svg', tile(color)+emblem())
for name in ['01-horizontal','02-stacked','03-mark']:
    source = ROOT / 'logo' / f'{name}.svg'
    for size in (512,192,48):
        target = ROOT / 'previews' / f'{name}-{size}.png'
        target.parent.mkdir(exist_ok=True)
        if name == '01-horizontal':
            temp=target.with_suffix('.tmp.png')
            subprocess.run(['inkscape',str(source),'--export-filename='+str(temp),'--export-width='+str(size)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            wide=Image.open(temp).convert('RGBA')
            canvas=Image.new('RGBA',(size,size),CREAM)
            canvas.alpha_composite(wide,(0,(size-wide.height)//2))
            canvas.save(target)
            temp.unlink()
        else:
            subprocess.run(['inkscape',str(source),'--export-filename='+str(target),'--export-width='+str(size)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
for name in ('terracotta','amber','coral'):
    for size in (512,192,48):
        source = ROOT / 'icon' / f'{name}.svg'
        target = ROOT / 'previews' / f'icon-{name}-{size}.png'
        subprocess.run(['inkscape',str(source),'--export-filename='+str(target),'--export-width='+str(size)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)

# A compact visual review sheet, also useful as an overview outside Figma.
board = Image.new('RGB',(1600,1240),CREAM)
d = ImageDraw.Draw(board)
sans = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
serif = '/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'
def label(x,y,s,n=25,bold=False):
    d.text((x,y),s,fill=INK,font=ImageFont.truetype(serif if bold else sans,n))
label(70,48,'Вкус детства',58,True)
label(72,126,'Знак • логотип • цвет • экран приложения',25)
rows=[('01 / Горизонтальный логотип','01-horizontal'),('02 / Вертикальный логотип','02-stacked'),('03 / Самостоятельный знак','03-mark')]
for idx,(title,key) in enumerate(rows):
    y=220+idx*260
    label(74,y,title,28,True)
    for x,size in ((730,192),(1030,120),(1260,48)):
        src=Image.open(ROOT/'previews'/f'{key}-{512 if size==192 else 192 if size==120 else 48}.png').convert('RGBA')
        src.thumbnail((size,size),Image.Resampling.LANCZOS)
        board.paste(src,(x,y),src)
    label(735,y+203,'512 px',19)
    label(1030,y+145,'192 px',19)
    label(1260,y+65,'48 px',19)
label(74,1010,'Три цветовых направления',30,True)
for i,(name,color) in enumerate([('Терракота',TERRA),('Янтарь',AMBER),('Коралл',CORAL)]):
    x=78+i*500
    d.rounded_rectangle((x,1070,x+115,1185),25,fill=color)
    label(x+135,1088,name,23,True)
    label(x+135,1127,color,20)
board.save(ROOT/'brand-board.png')
