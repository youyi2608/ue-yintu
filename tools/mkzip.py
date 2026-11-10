import sys, zipfile

src, dex, dst = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, 'w') as zo:
    for it in zi.infolist():
        data = zi.read(it.filename)
        new = zipfile.ZipInfo(it.filename, date_time=it.date_time)
        new.external_attr = it.external_attr
        # Android 11+ 要求 resources.arsc 未压缩存储
        new.compress_type = zipfile.ZIP_STORED if it.filename == 'resources.arsc' else it.compress_type
        zo.writestr(new, data)
    zo.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
print('ok', dst)