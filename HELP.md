在最近的开发中,我经常要对sql进行处理
1. 比如,给我一个sql文本,我要对sql中涉及的所有的下划线进行驼峰转下划线,或者下划线转驼峰
如果用传统的处理字符串的方式,我感觉太复杂和容易出所,而且也不容易维护,所以我决定用java模型来实现
比如,我先建立一个SqlBuilder类
```java
public class SqlFactory implements SqlParser, FieldEditor, TableNameMap {

}
interface SqlParser {
    // 根据sql文本建模过程
    String toSqlStr();
    // 模型转sql文本过程
    void fromSql(String sql);
}
interface FieldEditor {
    void 所有字段下划线转驼峰();
    void 所有字段驼峰转下划线();
}
// 表名映射能力, 初始sql文本中的表名需要修改为别的表名
interface TableNameMap {
    void addTableNameMap(String oldName, String newName);
}
```