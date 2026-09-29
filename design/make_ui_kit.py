"""Render a compact visual specification from brand colors, offline."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).parent
W,H=1500,1400
BG='#FBF5E9'; SURFACE='#FFF8EA'; INK='#312921'; PRIMARY='#A74731'; GREEN='#365847'; MUTED='#776E64'
im=Image.new('RGB',(W,H),BG); d=ImageDraw.Draw(im)
sans='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
serif='/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'
def txt(x,y,s,size=23,color=INK,ser=False): d.text((x,y),s,font=ImageFont.truetype(serif if ser else sans,size),fill=color)
def rect(box,fill,r=20,outline=None,width=1): d.rounded_rectangle(box,radius=r,fill=fill,outline=outline,width=width)
txt(70,55,'Вкус детства / UI-kit',47,ser=True)
txt(72,125,'Светлая и тёмная темы • компоненты • карточки семейного архива',21,MUTED)

# Recipe collection preview.
rect((70,205,650,945),SURFACE,30)
txt(100,238,'Семейные рецепты',36,ser=True)
rect((100,310,620,365),'#F0E8DC',13)
txt(122,322,'Найти рецепт или человека',23,MUTED)
rect((100,398,620,760),'#E8D2B5',22)
d.ellipse((260,424,465,629),fill='#C88850')
d.ellipse((291,454,435,597),fill='#D9A36A')
d.ellipse((317,475,413,574),fill='#F5CF8D')
txt(122,782,'Пирог бабушки Нины',30,ser=True)
txt(122,836,'45 мин   •   выпечка   •   12 раз готовили',19,MUTED)
rect((100,895,330,930),GREEN,17)
txt(123,899,'Семейная память',17,'#FFFFFF')

# Components column.
txt(720,205,'Элементы интерфейса',32,ser=True)
rect((720,275,1050,331),PRIMARY,13); txt(773,287,'Добавить рецепт',22,'#FFFFFF')
rect((1070,275,1390,331),SURFACE,13,GREEN,2); txt(1120,287,'Смотреть книгу',22,GREEN)
rect((720,363,1390,427),SURFACE,12,'#877D72',2); txt(742,382,'Название рецепта',23,MUTED)
txt(720,457,'Оценки семейного рецепта',23,ser=True)
for i,(label,n) in enumerate([('Легендарный вкус',5),('Простота приготовления',4),('Семейная память',5)]):
    y=512+i*67; txt(720,y,label,20)
    for j in range(5): d.ellipse((1180+j*40,y+1,1207+j*40,y+28),fill=PRIMARY if j<n else '#D5C7B8')
txt(720,740,'Кнопки, поле и оценки приведены в масштабе',19,MUTED)
txt(720,775,'спецификации; tap targets — от 48 dp.',19,MUTED)

# Dark family card.
rect((70,990,1390,1330),'#211D1A',28)
txt(105,1020,'Семья и память',33,'#F5EBDD',True)
rect((105,1085,810,1287),'#302923',20)
d.ellipse((140,1118,268,1246),fill='#6F6657')
txt(295,1115,'Нина Петровна',28,'#F5EBDD',True)
txt(295,1160,'Бабушка • 8 рецептов',19,'#D5C7B8')
txt(295,1202,'«Печь пирог по воскресеньям»',18,'#BDD7BF')
rect((850,1090,1345,1165),'#F0AA91',15)
txt(894,1105,'Как мы это едим',25,'#512416')
txt(853,1195,'Тёплый шоколадный фон',19,'#F5EBDD')
txt(853,1233,'без чистого чёрного',19,'#D5C7B8')
im.save(ROOT/'ui-kit-board.png')
