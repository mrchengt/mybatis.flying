package indi.mybatis.flying.statics;

/**
 * 
 * @date 2019年12月18日 11:56:08
 *
 * @author 李萌
 * @email limeng32@live.cn
 * @since JDK 1.8
 */
public enum KeyGeneratorType {
	/** Generate by UUID */
	uuid,
	/** Generated by UUID without a horizontal line */
	uuid_no_line,
	/** Timestamp that is accurate to millisecond */
	millisecond,
	/** The simple prototype of snowflake algorithm, for reference only */
	snowflake,
	/** User personalization handler */
	custom,
}
