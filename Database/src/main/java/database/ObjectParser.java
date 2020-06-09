package database;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectParser {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ObjectParser.class);
	
	private Database database;
	
	public ObjectParser(Database database) {
		this.database = database;
	}
	
	public byte[] parse(Object o) {
		try {
			Table table = getApplicableTable(o);
			int index = 0;
			byte[] rowBytes = new byte[table.getRowLength()];
			for(Column column : table.getColumns().values()) {
				byte[] fieldBytes = new byte[column.getLength()];
				String fieldName = column.getName();
				String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
				Object fieldValue = o.getClass().getMethod(getterName).invoke(o);
				byte[] objectFieldBytes = DataType.getBytes(fieldValue);
				System.arraycopy(objectFieldBytes, 0, fieldBytes, 0, objectFieldBytes.length);
				for(byte b : fieldBytes) {
					if(b == 0 && column.getDataType() == DataType.VARCHAR) {
						b = 32;
					}
				}
				System.arraycopy(fieldBytes, 0, rowBytes, index, column.getLength());
				index += column.getLength();
			}	
			return rowBytes;
		}
		catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			LOGGER.warn(e.getMessage());
		}
		return null;
	}
	
	public Object unparse(byte[] row, Table table) {
		try {
			String tableClassName = table.getName().substring(0, 1).toUpperCase() + table.getName().substring(1).toLowerCase();
			Class tableClass = Class.forName(tableClassName);
			Object o = tableClass.cast(new Object());
			for(Column column : table.getColumns().values()) {
				String fieldName = column.getName();
				String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
				byte[] fieldBytes = new byte[column.getLength()];
				System.arraycopy(row, table.getIndexInRow(column), fieldBytes, 0, column.getLength());
				o.getClass().getMethod(setterName).invoke(o, DataType.getValue(fieldBytes, column));
			}	
		}
		catch(ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			LOGGER.warn(e.getMessage());
		}
		return null;
	}
	
	private Table getApplicableTable(Object o) {
		return database.getTables().get(o.getClass().getSimpleName().toUpperCase());
	}
}
