"""Screenshot boxes for tools/contrast.py: (pair, where, shot name without .png, (x0, y0, x1, y1) in the 2560 px
screenshot, threshold). Boxes hug one piece of text plus its own background; re-pick them when the window layout moves.
Shots: tools/qa_tour.sh (v0.1.2: all 2D is one system window, x 723-1892, y 848-1708 in these shots)."""

SAMPLES = [
    # window glass
    ("labelPrimary / window glass", "level title (title 18 sp)", "S6", (765, 908, 985, 940), 4.5),
    ("labelPrimary / window glass", "question prompt (headline 24 sp)", "S6", (1051, 1250, 1548, 1290), 4.5),
    ("labelPrimary / window glass", "answer group label 通行 (label 14 sp)", "S6", (767, 1512, 810, 1532), 4.5),
    ("labelPrimary / window glass", "settings row title 语言", "SET", (766, 1017, 820, 1040), 4.5),
    ("labelPrimary / window glass", "settings sub line (label 14 sp)", "SET", (1331, 1155, 1633, 1175), 4.5),
    ("labelPrimary / window glass", "settings About line (label 14 sp)", "SET", (766, 1625, 1320, 1645), 4.5),
    ("labelPrimary / window glass", "verdict 'you said' line (title 18 sp)", "S7_P3", (767, 1047, 1080, 1075), 4.5),
    ("labelPrimary / window glass", "verdict explain line (body 15 sp)", "S7_P3", (825, 1107, 1183, 1130), 4.5),
    ("labelPrimary / window glass", "lesson step text (body 15 sp)", "S4_P4", (822, 1163, 1560, 1185), 4.5),
    ("labelPrimary / window glass", "lesson mnemonic (body 15 sp)", "S4_P4", (811, 1436, 1337, 1456), 4.5),
    ("labelPrimary / window glass", "lesson stick tip (label 14 sp)", "S4_P4", (811, 1650, 1001, 1670), 4.5),
    ("labelPrimary / window glass", "confirm title (headline 24 sp)", "SET_exit_dialog", (1179, 1175, 1419, 1205), 4.5),
    ("labelPrimary / window glass", "confirm body (body 15 sp)", "SET_exit_dialog", (1191, 1238, 1413, 1258), 4.5),
    ("labelPrimary / window glass", "volume percent 100% (label 14 sp)", "SET", (1209, 1210, 1261, 1232), 4.5),
    # fills on glass
    ("labelPrimary / fillTertiary", "key text: Sound", "S6", (1603, 910, 1657, 940), 4.5),
    ("labelPrimary / fillTertiary", "answer key: 进待转区", "S6", (1636, 1510, 1747, 1535), 4.5),
    ("labelPrimary / fillTertiary", "segment item not selected: EN", "SET", (1209, 1015, 1238, 1033), 4.5),
    ("labelPrimary / fillTertiary", "verdict key 看分解", "S7_P3", (1299, 1625, 1382, 1650), 4.5),
    ("labelPrimary / fillTertiary", "lesson Back key", "S4_P4", (1768, 910, 1823, 935), 4.5),
    ("labelPrimary / fillTertiary", "confirm Cancel", "SET_exit_dialog", (1206, 1340, 1267, 1365), 4.5),
    ("labelPrimary / fillSecondary", "question counter chip 1 / 1", "S6", (1457, 912, 1498, 938), 4.5),
    ("labelPrimary / fillSecondary", "verdict official chip 原文", "S7_P3", (1788, 1000, 1829, 1016), 4.5),
    ("labelPrimary / fillSecondary", "verdict explain number disc 2", "S7_P3", (779, 1163, 796, 1185), 4.5),
    ("labelPrimary / fillSecondary", "lesson current step row", "S4_P4", (822, 1100, 1818, 1122), 4.5),
    ("labelPrimary / fillSecondary", "lesson chip 第 3 / 8 课", "S4_P4", (1568, 912, 1665, 932), 4.5),
    # fixed fills, measured on the capture (the exact value is in the fixed table)
    ("ink / interaction fill (measured)", "selected segment 中文", "SET", (1088, 1013, 1130, 1033), 4.5),
    ("ink / interaction fill (measured)", "lane chip 你在：直行车道 (label 14 sp)", "S6", (784, 1000, 936, 1022), 4.5),
    ("ink / interaction fill (measured)", "verdict See score key", "S7_P3", (1741, 1625, 1823, 1650), 4.5),
    ("ink / interaction fill (measured)", "lesson Play key", "S4_P4", (1031, 1500, 1086, 1530), 4.5),
    ("ink / interaction fill (measured)", "leave confirm Keep going", "S12", (1363, 1340, 1484, 1365), 4.5),
    ("ink / passable fill (measured)", "verdict pill 对 (headline 24 sp)", "S7_P3", (852, 995, 887, 1020), 4.5),
    ("ink / error fill (measured)", "confirm Quit", "SET_exit_dialog", (1393, 1340, 1454, 1365), 4.5),
    ("ink / error fill (measured)", "settings Reset", "SET", (1765, 1235, 1860, 1280), 4.5),
    ("ink / error fill (measured)", "settings Quit app", "SET", (1713, 1603, 1823, 1632), 4.5),
]

# measured for the record, exempt from the gate (WCAG 1.4.3: inactive controls)
EXEMPT = []
