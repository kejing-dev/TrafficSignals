"""Screenshot boxes for tools/contrast.py: (pair, where, shot name without .png, (x0, y0, x1, y1) in the 2160 px
console screenshot, threshold). Boxes hug one piece of text plus its own background; re-pick them when a layout moves.
Shots: build/states-v011 (tools/qa_tour.sh + the extra list in ui-audit.md)."""

SAMPLES = [
    # Regular glass, P1 (asphalt + crosswalk behind)
    ("labelPrimary / P1 glass", "home one-liner (body 15 sp)", "S2", (700, 1315, 1200, 1345), 4.5),
    ("labelPrimary / P1 glass", "lesson list rows (title 18 sp)", "S16", (715, 1352, 868, 1376), 4.5),
    ("labelPrimary / P1 glass", "lesson list 'seen' (label 14 sp)", "S16", (1515, 1355, 1545, 1372), 4.5),
    ("labelPrimary icon / P1 glass", "lesson list check icon (graphic)", "S16", (1482, 1352, 1502, 1372), 3.0),
    ("labelPrimary / P1 glass", "settings sub line (label 14 sp)", "SET", (1138, 1516, 1327, 1537), 4.5),
    ("labelPrimary / P1 glass", "settings About line (label 14 sp)", "SET", (697, 1672, 1092, 1692), 4.5),
    # fills on glass
    ("labelPrimary / fillTertiary", "home card subtitle (label 14 sp)", "S2", (715, 1590, 925, 1612), 4.5),
    ("labelPrimary / fillTertiary", "key text: Settings (title 18 sp)", "S2", (1488, 1278, 1535, 1302), 4.5),
    ("labelPrimary / fillTertiary", "segment item not selected: EN", "SET", (1048, 1350, 1080, 1370), 4.5),
    ("labelPrimary / fillTertiary", "segment item not selected: 慢放 0.5×", "SET", (1458, 1350, 1530, 1370), 4.5),
    ("labelPrimary / fillTertiary", "key text on P2: Sound", "S6", (1003, 1266, 1055, 1294), 4.5),
    ("labelPrimary / fillTertiary", "answer key: 进待转区 (enabled)", "S6", (1008, 1518, 1090, 1546), 4.5),
    ("labelPrimary / fillSecondary", "question counter chip 1 / 1", "S6", (888, 1266, 935, 1296), 4.5),
    ("labelPrimary / fillSecondary", "verdict official chip 原文", "S7_P3", (1390, 1268, 1422, 1286), 4.5),
    ("labelPrimary / fillSecondary", "verdict explain number disc 2", "S7_P3", (812, 1375, 836, 1400), 4.5),
    ("labelPrimary / fillSecondary", "lesson current step row", "S4_P4", (810, 1125, 1240, 1142), 4.5),
    ("labelPrimary / fillSecondary", "lesson current step number", "S4_P4", (778, 1125, 792, 1145), 4.5),
    ("labelPrimary / fillSecondary", "lesson chip 第 3 / 8 课", "S4_P4", (1278, 998, 1348, 1016), 4.5),
    # P2 / P3 / P4 glass
    ("labelPrimary / P2 glass", "level title (title 18 sp)", "S6", (715, 1268, 870, 1292), 4.5),
    ("labelPrimary / P2 glass", "answer group label 通行 (label 14 sp)", "S6", (715, 1520, 750, 1544), 4.5),
    ("labelPrimary / P3 glass", "verdict explain line (body 15 sp)", "S7_P3", (850, 1348, 1100, 1366), 4.5),
    ("labelPrimary / P3 glass", "verdict 'you said' line (title 18 sp)", "S7_P3", (810, 1303, 1040, 1325), 4.5),
    ("labelPrimary / P4 glass", "lesson step text (body 15 sp)", "S4_P4", (810, 1055, 1220, 1070), 4.5),
    ("labelPrimary / fillTertiary", "lesson Home key", "S4_P4", (1420, 997, 1460, 1017), 4.5),
    # Thick glass dialog
    ("labelPrimary / DLG thick glass", "dialog body", "SET_exit_dialog", (862, 1425, 1030, 1445), 4.5),
    ("labelPrimary / DLG thick glass", "dialog title (headline 24 sp)", "SET_exit_dialog", (860, 1375, 1045, 1405), 4.5),
    ("labelPrimary / fillTertiary on thick glass", "dialog Cancel", "SET_exit_dialog", (1190, 1562, 1242, 1595), 4.5),
    # danger keys, measured (the exact value is in the fixed table)
    ("ink / error fill (measured)", "dialog Quit", "SET_exit_dialog", (1326, 1562, 1382, 1595), 4.5),
    ("ink / error fill (measured)", "dialog Quit, English", "S15_SET_exit_dialog", (1326, 1562, 1382, 1595), 4.5),
    ("ink / error fill (measured)", "reset dialog Reset, English", "S15_SET_reset_dialog", (1312, 1562, 1378, 1595), 4.5),
    ("ink / error fill (measured)", "S13 can't-open dialog Quit", "S13", (1326, 1562, 1378, 1593), 4.5),
    ("ink / interaction fill (measured)", "leave dialog Keep going", "S12", (1278, 1515, 1380, 1548), 4.5),
    ("ink / interaction fill (measured)", "Start key (56 dp)", "S5", (1472, 1590, 1530, 1620), 4.5),
    ("ink / interaction fill (measured)", "verdict See score key", "S7_P3", (1358, 1782, 1422, 1812), 4.5),
    ("ink / interaction fill (measured)", "selected segment 中文", "SET", (966, 1350, 1002, 1370), 4.5),
    ("ink / interaction fill (measured)", "lane chip 你在：直行车道 (label 14 sp)", "S6", (728, 1326, 837, 1350), 4.5),
    ("ink / passable fill (measured)", "verdict pill Nailed it (headline 24 sp)", "S15_S7_P3", (862, 1260, 968, 1292), 4.5),
    ("ink / error fill (measured)", "verdict pill Wrong (headline 24 sp)", "S15_S8_P3", (862, 1260, 965, 1292), 4.5),
    ("labelPrimary / fillTertiary", "verdict Inferred chip", "S15_S8_P3", (1360, 1263, 1432, 1287), 4.5),
    ("ink / error fill (measured)", "settings Reset", "SET", (1485, 1495, 1540, 1528), 4.5),
    ("ink / error fill (measured)", "settings Quit app", "SET", (1452, 1653, 1537, 1682), 4.5),
]

# measured for the record, exempt from the gate (WCAG 1.4.3: inactive controls / content behind a modal dialog)
EXEMPT = [
    ("disabled answer key after the verdict", "S7", (808, 1595, 885, 1615)),
    ("settings text dimmed behind the exit dialog", "SET_exit_dialog", (700, 1505, 760, 1530)),
]
