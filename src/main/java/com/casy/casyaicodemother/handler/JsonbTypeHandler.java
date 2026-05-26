package com.casy.casyaicodemother.handler;

import com.mybatisflex.core.handler.Fastjson2TypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL jsonb 字段专用的 MyBatis 类型转换器。
 * <p>
 * <b>背景：Java 类型与数据库类型不一致</b><br>
 * 实体类 {@code App.appTypes} 是 {@code List<String>}，数据库列 {@code app_types} 是 PostgreSQL 的 {@code jsonb}。
 * MyBatis 无法自动完成这种映射，需要通过 {@code TypeHandler} 在读写时做转换。
 * <p>
 * <b>父类 Fastjson2TypeHandler 做了什么</b><br>
 * - 写入：把 {@code List<String>} 序列化成 JSON 字符串，例如 {@code ["website","tool"]}<br>
 * - 读取：把数据库里的 JSON 字符串反序列化回 {@code List<String>}
 * <p>
 * <b>为什么还要继承并重写？</b><br>
 * 父类写入时使用 JDBC 的 {@code setString}，PostgreSQL 收到的是 {@code varchar} 类型。
 * 但 {@code app_types} 列声明为 {@code jsonb}，PG 会报错：
 * {@code 字段 "app_types" 的类型为 jsonb, 但表达式的类型为 character varying}
 * <p>
 * <b>本类的修复方式</b><br>
 * 写入时用 {@link PGobject} 包装 JSON 字符串，并显式声明类型为 {@code jsonb}，
 * 让 PostgreSQL 驱动以正确的类型传给数据库。读取逻辑仍沿用父类，无需改动。
 * <p>
 * <b>使用方式</b>（见 {@code App.appTypes}）：
 * <pre>
 * {@code @Column(value = "app_types", typeHandler = JsonbTypeHandler.class)}
 * private List<String> appTypes;
 * </pre>
 *
 * @see com.casy.casyaicodemother.model.entity.App#appTypes
 */
public class JsonbTypeHandler extends Fastjson2TypeHandler {

    public JsonbTypeHandler(Class<?> propertyType) {
        super(propertyType);
    }

    public JsonbTypeHandler(Class<?> propertyType, Class<?> genericType) {
        super(propertyType, genericType);
    }

    /**
     * 写入数据库前的参数绑定：Java 对象 → PostgreSQL jsonb。
     * <p>
     * 流程：{@code List<String>} → JSON 字符串 → {@link PGobject}(type=jsonb) → PreparedStatement
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(toJson(parameter));
        ps.setObject(i, pgObject);
    }
}
