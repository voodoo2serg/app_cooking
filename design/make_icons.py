"""Companion 24x24 outline icons; share one stroke and rounded terminals."""
from pathlib import Path

OUT = Path(__file__).parent / 'icons'
OUT.mkdir(exist_ok=True)
paths = {
    'recipe': '<path d="M5 3.5h12a2 2 0 0 1 2 2v15H7a2 2 0 0 1-2-2z M5 6.5H3v14h16 M9 9h6 M9 13h6 M9 17h4"/>',
    'person': '<circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-4 2.5-6.2 7-6.2s7 2.2 7 6.2z"/>',
    'search': '<circle cx="10.8" cy="10.8" r="6.4"/><path d="m15.5 15.5 5 5"/>',
    'camera': '<path d="M3 7h4l1.5-2h7L17 7h4v12H3z"/><circle cx="12" cy="13" r="3.2"/>',
    'mic': '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0 0 12 0 M12 17v4 M9 21h6"/>',
    'pdf': '<path d="M6 2.5h8l4 4v15H6z M14 2.5v4h4 M9 13h6 M9 16h4"/>',
    'heart': '<path d="M12 20s-8-4.8-8-10.5a4.2 4.2 0 0 1 8-1.8 4.2 4.2 0 0 1 8 1.8C20 15.2 12 20 12 20z"/>',
    'home': '<path d="m3 11 9-7 9 7 M5.5 10v10h13V10 M16 5.2V3h2.4v4.1"/>',
    'share': '<circle cx="18" cy="5" r="2"/><circle cx="6" cy="12" r="2"/><circle cx="18" cy="19" r="2"/><path d="m8 11 8-5 M8 13l8 5"/>',
    'settings': '<path d="M4 6h16 M4 12h16 M4 18h16"/><circle cx="9" cy="6" r="2" fill="#FFF8EA"/><circle cx="16" cy="12" r="2" fill="#FFF8EA"/><circle cx="10" cy="18" r="2" fill="#FFF8EA"/>',
    'premium': '<path d="m3 9 4 3 5-7 5 7 4-3-2 10H5z M5 21h14"/>',
}
for name,path in paths.items():
    (OUT/f'{name}.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#365847" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">'+path+'</svg>\n',encoding='utf8')
