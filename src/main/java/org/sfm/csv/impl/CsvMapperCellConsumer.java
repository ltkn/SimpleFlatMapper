package org.sfm.csv.impl;


import org.sfm.csv.parser.CellConsumer;

public interface CsvMapperCellConsumer extends CellConsumer {

    public void newCell(char[] chars, int offset, int length, int cellIndex);

}