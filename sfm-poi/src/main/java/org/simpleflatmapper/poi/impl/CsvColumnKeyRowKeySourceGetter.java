package org.simpleflatmapper.poi.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.simpleflatmapper.csv.CsvColumnKey;
import org.simpleflatmapper.map.context.KeySourceGetter;

public class CsvColumnKeyRowKeySourceGetter implements KeySourceGetter<CsvColumnKey, Row> {

    public static final CsvColumnKeyRowKeySourceGetter INSTANCE = new CsvColumnKeyRowKeySourceGetter();

    private CsvColumnKeyRowKeySourceGetter() {
    }

    @Override
    public Object getValue(CsvColumnKey key, Row source) {
        final Cell cell = source.getCell(key.getIndex());
        if (cell != null) {
            switch (cell.getCellType()) {
                case BLANK:
                    return null;
                case BOOLEAN:
                    return cell.getBooleanCellValue();
                case NUMERIC:
                    return cell.getNumericCellValue();
                default:
                    return cell.getStringCellValue();
            }
        }
        return null;
    }
}
