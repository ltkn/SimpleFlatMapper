module org.simpleflatmapper.poi {
        requires transitive org.apache.poi.poi;

        requires transitive org.simpleflatmapper.map;
        requires org.simpleflatmapper.csv;

        exports org.simpleflatmapper.poi;



}