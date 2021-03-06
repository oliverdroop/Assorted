package database;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum DataType {
	BOOLEAN(1, boolean.class), 
	BYTE(1, byte.class), 
	INT(4, int.class), 
	LONG(8, long.class), 
	DOUBLE(8, double.class), 
	VARCHAR(2, String.class);
	private static final Logger LOGGER = LoggerFactory.getLogger(DataType.class);
	private final int length;
	private final Class type;
	
	DataType(int length, Class clazz){
		this.length = length;
		this.type = clazz;
	}
	
	public int getLength() {
		return length;
	}

	public <T> Class<T> getType() {
		return type;
	}
	
	public static byte[] getBytes(Object o) {
		byte[] out = new byte[0];
		if (o instanceof Boolean) {
			out = new byte[1];
			out[0] = (byte)((boolean) o? 1 : 0);
			return out;
		}
		if (o instanceof Byte) {
			out = new byte[1];
			out[0] = (byte)o;
			return out;
		}
		if (o instanceof Integer) {
			out = new byte[4];
			out = ByteBuffer.allocate(4).putInt((int)o).array();
			return out;
		}
		if (o instanceof Long) {
			out = new byte[8];
			out = ByteBuffer.allocate(8).putLong((long)o).array();
			return out;
		}
		if (o instanceof Double) {
			out = new byte[8];
			out = ByteBuffer.allocate(8).putDouble((double)o).array();
			return out;
		}
		if (o instanceof String) {
			out = ((String) o).getBytes();
			return out;
		}
		if (o instanceof byte[]) {
			return (byte[]) o;
		}
		return null;
	}
	
	public byte[] getBytes(String value) {
		Object o = null;
		if (this == DataType.BOOLEAN){
			if (value.length() == 1) {
				o = Byte.parseByte(value);
			}
			if (value.equals("true")) {
				o = Byte.parseByte("1");
			}
			if (value.equals("false")) {
				o = Byte.parseByte("0");
			}
		}
		if (this == DataType.BYTE){
			o = Byte.parseByte(value);
		}
		if (this == DataType.INT){
			o = Integer.parseInt(value);
		}
		if (this == DataType.LONG){
			o = Long.parseLong(value);
		}
		if (this == DataType.DOUBLE){
			o = Double.parseDouble(value);
		}
		if (this == DataType.VARCHAR){
			o = value;
		}
		if (o != null) {
			return getBytes(o);
		} else {
			return new byte[0];
		}
	}
	
	public Object getValue(byte[] fieldBytes) {
		if (this == DataType.BOOLEAN) {
			return fieldBytes[0] != 0;
		}
		if (this == DataType.BYTE) {
			return fieldBytes[0];
		}
		if (this == DataType.INT) {
		    return ByteBuffer.wrap(fieldBytes).getInt();
		}
		if (this == DataType.LONG) {
		    return ByteBuffer.wrap(fieldBytes).getLong();
		}
		if (this == DataType.DOUBLE) {
		    return ByteBuffer.wrap(fieldBytes).getDouble();
		}
		if (this == DataType.VARCHAR) {
			return new String(fieldBytes, StandardCharsets.UTF_8);
		}
		return null;
	}
	
	public String getValueString(byte[] fieldBytes) {
		if (this == DataType.BOOLEAN) {
			return ((boolean) (fieldBytes[0] != 0) ? "true" : "false");
		}
		if (this.isNumeric()) {
			return String.valueOf(getValue(fieldBytes));
		}
		if (this == DataType.VARCHAR) {
			return ((String) getValue(fieldBytes)).trim();
		}
		return null;
	}
	
	public static byte[] increment(byte[] bytes) {
		long outputLong = ByteBuffer.wrap(bytes).getLong() + 1;
		return ByteBuffer.allocate(bytes.length).putLong(outputLong).array();
	}
	
	public boolean isNumeric() {
		return this == DataType.BYTE || this == DataType.INT
				|| this == DataType.LONG || this == DataType.DOUBLE;
	}
}
