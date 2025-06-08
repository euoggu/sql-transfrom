package com.example.testar;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class SqlFactory implements SelectAsName, SqlParser, FieldEditor, TableNameMap {

    private SqlStatement sqlStatement;
    private Map<String, String> tableNameMapping = new HashMap<>();

    @Override
    public void fromSql(String sql) {
        this.sqlStatement = parseSql(sql);
    }

    @Override
    public String toSqlStr() {
        if (sqlStatement == null) {
            return "";
        }
        return sqlStatement.toSql();
    }

    @Override
    public void 所有字段下划线转驼峰() {
        if (sqlStatement != null) {
            sqlStatement.convertFieldsToCamelCase();
        }
    }

    @Override
    public void 所有字段驼峰转下划线() {
        if (sqlStatement != null) {
            sqlStatement.convertFieldsToUnderscore();
        }
    }

    @Override
    public void addTableNameMap(String oldName, String newName) {
        tableNameMapping.put(oldName.toLowerCase(), newName);
        if (sqlStatement != null) {
            sqlStatement.applyTableMapping(tableNameMapping);
        }
    }

    private SqlStatement parseSql(String sql) {
        sql = sql.trim();
        String upperSql = sql.toUpperCase();

        if (upperSql.startsWith("SELECT")) {
            return new SelectStatement(sql);
        } else if (upperSql.startsWith("INSERT")) {
            return new InsertStatement(sql);
        } else if (upperSql.startsWith("UPDATE")) {
            return new UpdateStatement(sql);
        } else if (upperSql.startsWith("DELETE")) {
            return new DeleteStatement(sql);
        }

        throw new UnsupportedOperationException("Unsupported SQL type");
    }

    // 工具方法：下划线转驼峰
    private static String underscoreToCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;

        for (char c : input.toCharArray()) {
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    result.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }

        return result.toString();
    }

    // 工具方法：驼峰转下划线
    private static String camelCaseToUnderscore(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }

        return result.toString();
    }

    @Override
    public void 所有查询字段软添加驼峰别名() {
        if (sqlStatement != null && sqlStatement instanceof SelectStatement) {
            ((SelectStatement) sqlStatement).addCamelCaseAliases();
        }
    }

    // SQL语句基类
    abstract class SqlStatement {
        protected String originalSql;
        protected List<String> fields = new ArrayList<>();
        protected Map<String, String> tableAliases = new HashMap<>();
        protected List<String> tables = new ArrayList<>();

        public SqlStatement(String sql) {
            this.originalSql = sql;
            try {
                parse();
            } catch (Exception e) {
                System.err.println("Failed to parse SQL: " + sql);
                e.printStackTrace();
                // 保留原始SQL，避免后续空指针
                fields = new ArrayList<>();
                tables = new ArrayList<>();
            }
        }

        protected abstract void parse();

        public abstract String toSql();

        public void convertFieldsToCamelCase() {
            fields = fields.stream()
                    .map(SqlFactory::underscoreToCamelCase)
                    .collect(Collectors.toList());
        }

        public void convertFieldsToUnderscore() {
            fields = fields.stream()
                    .map(SqlFactory::camelCaseToUnderscore)
                    .collect(Collectors.toList());
        }

        public void applyTableMapping(Map<String, String> mapping) {
            for (int i = 0; i < tables.size(); i++) {
                String table = tables.get(i);
                String newName = mapping.get(table.toLowerCase());
                if (newName != null) {
                    tables.set(i, newName);
                }
            }
        }

        protected String processField(String field) {
            // 处理带表别名的字段，如 t.field_name
            if (field.contains(".")) {
                String[] parts = field.split("\\.", 2);
                return parts[0] + "." + parts[1];
            }
            return field;
        }
    }

    // SELECT语句实现
    class SelectStatement extends SqlStatement {
        private String whereClause = "";
        private String groupByClause = "";
        private String orderByClause = "";
        private String havingClause = "";
        private boolean hasDistinct = false;

        public SelectStatement(String sql) {
            super(sql);
            // 由于Java内部类字段初始化顺序问题，需要在构造函数中重新解析
            reparseSelectData();
        }

        private void reparseSelectData() {
            // 重新解析SELECT语句的各个子句
            String upperSql = originalSql.toUpperCase();

            // 找到各个子句的位置
            int fromIndex = upperSql.indexOf(" FROM ");
            if (fromIndex != -1) {
                String afterFrom = originalSql.substring(fromIndex + 6);
                reparseFromClause(afterFrom);
            }
        }

        private void reparseFromClause(String fromClause) {
            String upperClause = fromClause.toUpperCase();

            // 找到各个子句的位置
            int wherePos = upperClause.indexOf(" WHERE ");
            int groupByPos = upperClause.indexOf(" GROUP BY ");
            int havingPos = upperClause.indexOf(" HAVING ");
            int orderByPos = upperClause.indexOf(" ORDER BY ");

            // 提取WHERE子句
            if (wherePos != -1) {
                int whereStart = wherePos + 7; // " WHERE ".length() = 7
                int whereEnd = fromClause.length();
                if (groupByPos > wherePos) whereEnd = groupByPos;
                else if (orderByPos > wherePos) whereEnd = orderByPos;

                whereClause = fromClause.substring(whereStart, whereEnd).trim();
            }

            // 提取其他子句
            if (groupByPos != -1) {
                int groupByStart = groupByPos + 10; // " GROUP BY ".length() = 10
                int groupByEnd = fromClause.length();
                if (havingPos > groupByPos) groupByEnd = havingPos;
                else if (orderByPos > groupByPos) groupByEnd = orderByPos;

                groupByClause = fromClause.substring(groupByStart, groupByEnd).trim();
            }

            if (havingPos != -1) {
                int havingStart = havingPos + 8; // " HAVING ".length() = 8
                int havingEnd = fromClause.length();
                if (orderByPos > havingPos) havingEnd = orderByPos;

                havingClause = fromClause.substring(havingStart, havingEnd).trim();
            }

            if (orderByPos != -1) {
                int orderByStart = orderByPos + 10; // " ORDER BY ".length() = 10
                orderByClause = fromClause.substring(orderByStart).trim();
            }
        }

        @Override
        protected void parse() {
            // 更精确的解析实现
            String upperSql = originalSql.toUpperCase();

            // 找到各个子句的位置
            int fromIndex = upperSql.indexOf(" FROM ");
            if (fromIndex == -1) {
                throw new IllegalArgumentException("Invalid SELECT statement: missing FROM");
            }

            // 提取SELECT和FROM之间的字段
            String selectPart = originalSql.substring(0, fromIndex);
            String afterFrom = originalSql.substring(fromIndex + 6);

            // 检查DISTINCT
            if (selectPart.toUpperCase().contains("SELECT DISTINCT")) {
                hasDistinct = true;
                selectPart = selectPart.substring(selectPart.toUpperCase().indexOf("DISTINCT") + 8);
            } else {
                selectPart = selectPart.substring(selectPart.toUpperCase().indexOf("SELECT") + 6);
            }

            // 解析字段
            parseFields(selectPart.trim());

            // 解析FROM后的部分
            parseFromClause(afterFrom);
        }

        private void parseFromClause(String fromClause) {
            String upperClause = fromClause.toUpperCase();

            // 找到各个子句的位置
            int wherePos = upperClause.indexOf(" WHERE ");
            int groupByPos = upperClause.indexOf(" GROUP BY ");
            int havingPos = upperClause.indexOf(" HAVING ");
            int orderByPos = upperClause.indexOf(" ORDER BY ");

            // 确定表部分的结束位置
            int endPos = fromClause.length();
            if (wherePos != -1) endPos = Math.min(endPos, wherePos);
            if (groupByPos != -1) endPos = Math.min(endPos, groupByPos);
            if (orderByPos != -1) endPos = Math.min(endPos, orderByPos);

            // 提取表部分
            String tablesPart = fromClause.substring(0, endPos).trim();
            parseTables(tablesPart);

            // 提取各个子句 - 只保存子句内容，不包含关键字
            if (wherePos != -1) {
                int whereStart = wherePos + 7; // " WHERE ".length() = 7
                int whereEnd = fromClause.length();
                if (groupByPos > wherePos) whereEnd = groupByPos;
                else if (orderByPos > wherePos) whereEnd = orderByPos;

                whereClause = fromClause.substring(whereStart, whereEnd).trim();
            }

            if (groupByPos != -1) {
                int groupByStart = groupByPos + 10; // " GROUP BY ".length() = 10
                int groupByEnd = fromClause.length();
                if (havingPos > groupByPos) groupByEnd = havingPos;
                else if (orderByPos > groupByPos) groupByEnd = orderByPos;

                groupByClause = fromClause.substring(groupByStart, groupByEnd).trim();
            }

            if (havingPos != -1) {
                int havingStart = havingPos + 8; // " HAVING ".length() = 8
                int havingEnd = fromClause.length();
                if (orderByPos > havingPos) havingEnd = orderByPos;

                havingClause = fromClause.substring(havingStart, havingEnd).trim();
            }

            if (orderByPos != -1) {
                int orderByStart = orderByPos + 10; // " ORDER BY ".length() = 10
                orderByClause = fromClause.substring(orderByStart).trim();
            }
        }

        private void parseFields(String fieldsStr) {
            if (fieldsStr.trim().equals("*")) {
                fields.add("*");
                return;
            }

            String[] fieldArray = fieldsStr.split(",");
            for (String field : fieldArray) {
                field = field.trim();
                // 保留完整的字段表达式（包括别名）
                fields.add(field);
            }
        }

        private void parseTables(String tablesStr) {
            // 简单处理，支持逗号分隔的表和JOIN
            String[] tableArray = tablesStr.split("\\s*,\\s*|\\s+JOIN\\s+", -1);
            for (String tableExpr : tableArray) {
                tableExpr = tableExpr.trim();
                if (tableExpr.isEmpty()) continue;

                // 提取表名（处理别名）
                String[] parts = tableExpr.split("\\s+");
                if (parts.length > 0) {
                    String tableName = parts[0];
                    tables.add(tableName);

                    // 如果有别名
                    if (parts.length > 1 && !parts[1].equalsIgnoreCase("ON")) {
                        tableAliases.put(parts[1], tableName);
                    }
                }
            }
        }

        @Override
        public String toSql() {
            StringBuilder sql = new StringBuilder("SELECT ");

            if (hasDistinct) {
                sql.append("DISTINCT ");
            }

            // 构建字段列表
            if (fields.size() == 1 && fields.get(0).equals("*")) {
                sql.append("*");
            } else {
                sql.append(String.join(", ", fields));
            }

            sql.append(" FROM ");

            // 构建表名列表
            sql.append(String.join(", ", tables));

            // 添加所有子句 - 现在子句内容不包含关键字，所以需要添加
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }

            if (!groupByClause.isEmpty()) {
                sql.append(" GROUP BY ").append(groupByClause);
            }

            if (!havingClause.isEmpty()) {
                sql.append(" HAVING ").append(havingClause);
            }

            if (!orderByClause.isEmpty()) {
                sql.append(" ORDER BY ").append(orderByClause);
            }

            return sql.toString();
        }

        @Override
        public void convertFieldsToCamelCase() {
            if (fields.size() == 1 && fields.get(0).equals("*")) {
                return;
            }

            // 转换SELECT子句中的字段
            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                fields.set(i, convertFieldExpression(field, true));
            }

            // 转换WHERE子句中的字段名
            whereClause = convertFieldNamesInClause(whereClause, true);

            // 转换其他子句中的字段名
            groupByClause = convertFieldNamesInClause(groupByClause, true);
            orderByClause = convertFieldNamesInClause(orderByClause, true);
            havingClause = convertFieldNamesInClause(havingClause, true);
        }

        @Override
        public void convertFieldsToUnderscore() {
            if (fields.size() == 1 && fields.get(0).equals("*")) {
                return;
            }

            // 转换SELECT子句中的字段
            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                fields.set(i, convertFieldExpression(field, false));
            }

            // 转换WHERE子句中的字段名
            whereClause = convertFieldNamesInClause(whereClause, false);

            // 转换其他子句中的字段名
            groupByClause = convertFieldNamesInClause(groupByClause, false);
            orderByClause = convertFieldNamesInClause(orderByClause, false);
            havingClause = convertFieldNamesInClause(havingClause, false);
        }

        // 转换字段表达式（包括处理别名）
        private String convertFieldExpression(String fieldExpression, boolean toCamelCase) {
            // 检查是否有别名
            if (fieldExpression.toUpperCase().contains(" AS ")) {
                String[] parts = fieldExpression.split("\\s+(?i)AS\\s+", 2);
                String fieldPart = parts[0].trim();
                String aliasPart = parts[1].trim();

                // 只转换字段名部分，保留别名不变
                String convertedField = convertSingleField(fieldPart, toCamelCase);
                return convertedField + " AS " + aliasPart;
            } else {
                // 没有别名，直接转换字段名
                return convertSingleField(fieldExpression, toCamelCase);
            }
        }

        // 转换单个字段名
        private String convertSingleField(String field, boolean toCamelCase) {
            if (field.contains(".")) {
                // 处理带表别名的字段，如 t.field_name
                String[] parts = field.split("\\.", 2);
                String tablePart = parts[0];
                String fieldPart = parts[1];

                String convertedFieldPart = toCamelCase ?
                    underscoreToCamelCase(fieldPart) :
                    camelCaseToUnderscore(fieldPart);
                return tablePart + "." + convertedFieldPart;
            } else {
                // 普通字段名
                return toCamelCase ?
                    underscoreToCamelCase(field) :
                    camelCaseToUnderscore(field);
            }
        }

        // 转换子句中的字段名
        private String convertFieldNamesInClause(String clause, boolean toCamelCase) {
            if (clause == null || clause.isEmpty()) {
                return clause;
            }

            // 简单的字段名转换，匹配标识符模式
            // 这个正则表达式匹配字段名（包括带表别名的字段）
            Pattern fieldPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\b");
            Matcher matcher = fieldPattern.matcher(clause);

            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                String fieldName = matcher.group(1);
                String convertedField;

                if (fieldName.contains(".")) {
                    // 处理带表别名的字段，如 t.field_name
                    String[] parts = fieldName.split("\\.", 2);
                    String tablePart = parts[0];
                    String fieldPart = parts[1];

                    // 只转换字段部分，不转换表别名
                    String convertedFieldPart = toCamelCase ?
                        underscoreToCamelCase(fieldPart) :
                        camelCaseToUnderscore(fieldPart);
                    convertedField = tablePart + "." + convertedFieldPart;
                } else {
                    // 普通字段名
                    convertedField = toCamelCase ?
                        underscoreToCamelCase(fieldName) :
                        camelCaseToUnderscore(fieldName);
                }

                matcher.appendReplacement(result, convertedField);
            }
            matcher.appendTail(result);

            return result.toString();
        }

        // 为所有查询字段添加驼峰别名
        public void addCamelCaseAliases() {
            if (fields.size() == 1 && fields.get(0).equals("*")) {
                // 对于SELECT *，不添加别名
                return;
            }

            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);

                // 跳过已经有别名的字段
                if (field.toUpperCase().contains(" AS ")) {
                    continue;
                }

                // 提取实际的字段名（去掉表别名）
                String actualFieldName;
                if (field.contains(".")) {
                    // 处理带表别名的字段，如 t.field_name
                    String[] parts = field.split("\\.", 2);
                    actualFieldName = parts[1];
                } else {
                    // 普通字段名
                    actualFieldName = field;
                }

                // 生成驼峰别名
                String camelCaseAlias = underscoreToCamelCase(actualFieldName);

                // 只有当驼峰别名与原字段名不同时才添加别名
                if (!camelCaseAlias.equals(actualFieldName)) {
                    fields.set(i, field + " AS " + camelCaseAlias);
                }
            }
        }
    }

    // INSERT语句实现
    class InsertStatement extends SqlStatement {
        private List<String> values = new ArrayList<>();

        public InsertStatement(String sql) {
            super(sql);
            // 由于Java内部类字段初始化顺序问题，需要在构造函数中重新解析VALUES
            reparseValues();
        }

        private void reparseValues() {
            // 确保values列表被初始化
            if (values == null) {
                values = new ArrayList<>();
            } else {
                values.clear(); // 清空可能存在的旧数据
            }

            // 重新解析VALUES
            Pattern insertPattern = Pattern.compile(
                    "INSERT\\s+INTO\\s+([\\w\\.]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            Matcher matcher = insertPattern.matcher(originalSql);
            if (matcher.find()) {
                String valuesStr = matcher.group(3);
                if (valuesStr != null) {
                    List<String> parsedValues = parseValues(valuesStr);
                    values.addAll(parsedValues);
                }
            }
        }

        @Override
        protected void parse() {
            // 确保values列表被初始化（解决Java内部类字段初始化顺序问题）
            if (values == null) {
                values = new ArrayList<>();
            }

            // 更灵活的正则表达式，支持表名中的点号和空格
            Pattern insertPattern = Pattern.compile(
                    "INSERT\\s+INTO\\s+([\\w\\.]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            Matcher matcher = insertPattern.matcher(originalSql);

            if (matcher.find()) {
                String tableName = matcher.group(1);
                tables.add(tableName);

                String fieldsStr = matcher.group(2);
                if (fieldsStr != null) {
                    String[] fieldArray = fieldsStr.split(",");
                    for (String field : fieldArray) {
                        if (field != null) {
                            fields.add(field.trim());
                        }
                    }
                }

                String valuesStr = matcher.group(3);
                if (valuesStr != null) {
                    // 处理值，保留引号
                    List<String> parsedValues = parseValues(valuesStr);
                    values.addAll(parsedValues);
                }
            } else {
                // 如果正则匹配失败，保留原始SQL
                System.err.println("Unable to parse INSERT statement: " + originalSql);
                // 添加一个空表名，避免后续空指针
                tables.add("unknown_table");
            }
        }

        // 新增方法：解析VALUES部分，正确处理引号内的逗号
        private List<String> parseValues(String valuesStr) {
            List<String> result = new ArrayList<>();
            StringBuilder currentValue = new StringBuilder();
            boolean inQuotes = false;
            char quoteChar = '\0';

            for (int i = 0; i < valuesStr.length(); i++) {
                char c = valuesStr.charAt(i);

                if (!inQuotes && (c == '\'' || c == '"')) {
                    inQuotes = true;
                    quoteChar = c;
                    currentValue.append(c);
                } else if (inQuotes && c == quoteChar && (i + 1 >= valuesStr.length() || valuesStr.charAt(i + 1) != quoteChar)) {
                    inQuotes = false;
                    quoteChar = '\0';
                    currentValue.append(c);
                } else if (!inQuotes && c == ',') {
                    result.add(currentValue.toString().trim());
                    currentValue = new StringBuilder();
                } else {
                    currentValue.append(c);
                }
            }

            // 添加最后一个值
            if (currentValue.length() > 0) {
                result.add(currentValue.toString().trim());
            }

            return result;
        }

        @Override
        public String toSql() {
            if (tables.isEmpty() || fields.isEmpty()) {
                return originalSql; // 返回原始SQL
            }

            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(tables.get(0));
            sql.append(" (");
            sql.append(String.join(", ", fields));
            sql.append(") VALUES (");
            sql.append(String.join(", ", values));
            sql.append(")");

            return sql.toString();
        }

        // 重写字段转换方法，确保values列表保持同步
        @Override
        public void convertFieldsToCamelCase() {
            // 保存原始values，因为父类方法可能会影响它们
            List<String> originalValues = new ArrayList<>(values);

            fields = fields.stream()
                    .map(SqlFactory::underscoreToCamelCase)
                    .collect(Collectors.toList());

            // 恢复values列表，因为它们是值而不是字段名，不应该被转换
            values = originalValues;
        }

        @Override
        public void convertFieldsToUnderscore() {
            // 保存原始values，因为父类方法可能会影响它们
            List<String> originalValues = new ArrayList<>(values);

            fields = fields.stream()
                    .map(SqlFactory::camelCaseToUnderscore)
                    .collect(Collectors.toList());

            // 恢复values列表，因为它们是值而不是字段名，不应该被转换
            values = originalValues;
        }
    }

    // UPDATE语句实现
    class UpdateStatement extends SqlStatement {
        private Map<String, String> setValues = new LinkedHashMap<>();
        private String whereClause = "";

        public UpdateStatement(String sql) {
            super(sql);
            // 由于Java内部类字段初始化顺序问题，需要在构造函数中重新解析
            reparseUpdateData();
        }

        private void reparseUpdateData() {
            // 确保字段被初始化
            if (setValues == null) {
                setValues = new LinkedHashMap<>();
            } else {
                setValues.clear();
            }

            // 清空fields列表，避免重复添加
            fields.clear();

            // 重新解析UPDATE语句
            Pattern updatePattern = Pattern.compile(
                    "UPDATE\\s+([\\w\\.]+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            Matcher matcher = updatePattern.matcher(originalSql);
            if (matcher.find()) {
                String setClause = matcher.group(2);
                if (setClause != null) {
                    parseSetClause(setClause);
                }

                String whereGroup = matcher.group(3);
                whereClause = whereGroup != null ? whereGroup.trim() : "";
                System.err.println("DEBUG: Reparsed UPDATE - setValues: " + setValues + ", whereClause: '" + whereClause + "'");
            }
        }

        @Override
        protected void parse() {
            // 初始化，避免空指针
            setValues = new LinkedHashMap<>();

            Pattern updatePattern = Pattern.compile(
                    "UPDATE\\s+([\\w\\.]+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            Matcher matcher = updatePattern.matcher(originalSql);

            if (matcher.find()) {
                String tableName = matcher.group(1);
                if (tableName != null) {
                    tables.add(tableName);
                }

                String setClause = matcher.group(2);
                System.err.println("DEBUG: UPDATE setClause: '" + setClause + "'");
                if (setClause != null) {
                    // 解析SET子句 - 改进解析逻辑以处理带引号的值
                    parseSetClause(setClause);
                }

                // WHERE子句是第3组
                String whereGroup = matcher.group(3);
                whereClause = whereGroup != null ? whereGroup.trim() : "";
                System.err.println("DEBUG: UPDATE whereClause: '" + whereClause + "'");
            } else {
                // 解析失败时保留原始SQL
                System.err.println("Unable to parse UPDATE statement: " + originalSql);
                tables.add("unknown_table");
            }
        }

        private void parseSetClause(String setClause) {
            System.err.println("DEBUG: parseSetClause called with: '" + setClause + "'");

            // 改进的SET子句解析，处理带引号的值
            List<String> setPairs = new ArrayList<>();
            StringBuilder currentPair = new StringBuilder();
            boolean inQuotes = false;
            char quoteChar = '\0';

            for (int i = 0; i < setClause.length(); i++) {
                char c = setClause.charAt(i);

                if (!inQuotes && (c == '\'' || c == '"')) {
                    inQuotes = true;
                    quoteChar = c;
                    currentPair.append(c);
                } else if (inQuotes && c == quoteChar) {
                    inQuotes = false;
                    quoteChar = '\0';
                    currentPair.append(c);
                } else if (!inQuotes && c == ',') {
                    setPairs.add(currentPair.toString().trim());
                    currentPair = new StringBuilder();
                } else {
                    currentPair.append(c);
                }
            }

            // 添加最后一个pair
            if (currentPair.length() > 0) {
                setPairs.add(currentPair.toString().trim());
            }

            System.err.println("DEBUG: setPairs: " + setPairs);

            // 解析每个键值对
            for (String pair : setPairs) {
                String[] parts = pair.split("\\s*=\\s*", 2);
                System.err.println("DEBUG: Processing pair: '" + pair + "' -> " + Arrays.toString(parts));
                if (parts.length == 2) {
                    String field = parts[0].trim();
                    String value = parts[1].trim();
                    fields.add(field);
                    setValues.put(field, value);
                    System.err.println("DEBUG: Added field: '" + field + "' = '" + value + "'");
                }
            }

            System.err.println("DEBUG: Final setValues: " + setValues);
        }

        @Override
        public String toSql() {
            if (tables.isEmpty() || fields.isEmpty()) {
                return originalSql; // 返回原始SQL
            }

            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(tables.get(0));
            sql.append(" SET ");

            List<String> setPairs = new ArrayList<>();
            for (String field : fields) {
                String value = setValues.get(field);
                if (value != null) {
                    setPairs.add(field + " = " + value);
                } else {
                    System.err.println("DEBUG: No value found for field: " + field);
                    System.err.println("DEBUG: Available setValues: " + setValues);
                }
            }
            sql.append(String.join(", ", setPairs));

            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }

            return sql.toString();
        }

        @Override
        public void convertFieldsToCamelCase() {
            System.err.println("DEBUG: UpdateStatement.convertFieldsToCamelCase() called");
            System.err.println("DEBUG: Original fields: " + fields);
            System.err.println("DEBUG: Original setValues: " + setValues);
            System.err.println("DEBUG: Original whereClause: " + whereClause);

            // 转换SET子句中的字段名
            Map<String, String> newSetValues = new LinkedHashMap<>();
            List<String> newFields = new ArrayList<>();

            for (String field : fields) {
                String newField = underscoreToCamelCase(field);
                newFields.add(newField);
                newSetValues.put(newField, setValues.get(field));
            }

            fields = newFields;
            setValues = newSetValues;

            // 转换WHERE子句中的字段名
            whereClause = convertFieldNamesInWhereClause(whereClause, true);

            System.err.println("DEBUG: New fields: " + fields);
            System.err.println("DEBUG: New setValues: " + setValues);
            System.err.println("DEBUG: Final whereClause: " + whereClause);
        }

        @Override
        public void convertFieldsToUnderscore() {
            // 转换SET子句中的字段名
            Map<String, String> newSetValues = new LinkedHashMap<>();
            List<String> newFields = new ArrayList<>();

            for (String field : fields) {
                String newField = camelCaseToUnderscore(field);
                newFields.add(newField);
                newSetValues.put(newField, setValues.get(field));
            }

            fields = newFields;
            setValues = newSetValues;

            // 转换WHERE子句中的字段名
            whereClause = convertFieldNamesInWhereClause(whereClause, false);
        }

        // 转换WHERE子句中的字段名
        private String convertFieldNamesInWhereClause(String clause, boolean toCamelCase) {
            if (clause == null || clause.isEmpty()) {
                return clause;
            }

            // 简单的字段名转换，匹配标识符模式
            // 这个正则表达式匹配字段名（包括带表别名的字段）
            Pattern fieldPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\b");
            Matcher matcher = fieldPattern.matcher(clause);

            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                String fieldName = matcher.group(1);
                String convertedField;

                if (fieldName.contains(".")) {
                    // 处理带表别名的字段，如 t.field_name
                    String[] parts = fieldName.split("\\.", 2);
                    String tablePart = parts[0];
                    String fieldPart = parts[1];

                    // 只转换字段部分，不转换表别名
                    String convertedFieldPart = toCamelCase ?
                        underscoreToCamelCase(fieldPart) :
                        camelCaseToUnderscore(fieldPart);
                    convertedField = tablePart + "." + convertedFieldPart;
                } else {
                    // 普通字段名
                    convertedField = toCamelCase ?
                        underscoreToCamelCase(fieldName) :
                        camelCaseToUnderscore(fieldName);
                }

                matcher.appendReplacement(result, convertedField);
            }
            matcher.appendTail(result);

            return result.toString();
        }
    }

    // DELETE语句实现
    class DeleteStatement extends SqlStatement {
        private String whereClause = "";

        public DeleteStatement(String sql) {
            super(sql);
        }

        @Override
        protected void parse() {
            Pattern deletePattern = Pattern.compile(
                    "DELETE\\s+FROM\\s+([\\w\\.]+)(?:\\s+WHERE\\s+(.+))?$",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = deletePattern.matcher(originalSql);

            if (matcher.find()) {
                String tableName = matcher.group(1);
                tables.add(tableName);
                String whereGroup = matcher.group(2);
                whereClause = whereGroup != null ? whereGroup.trim() : "";
            }
        }

        @Override
        public String toSql() {
            StringBuilder sql = new StringBuilder("DELETE FROM ");
            sql.append(tables.get(0));

            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }

            return sql.toString();
        }
    }

    // 测试方法
    public static void main(String[] args) {
        SqlFactory factory = new SqlFactory();

        // 测试1: SELECT语句字段转换
        System.out.println("=== 测试1: SELECT语句字段转换 ===");
        String selectSql = "SELECT user_id, user_name, create_time FROM user_info WHERE user_id = 1";
        factory.fromSql(selectSql);
        System.out.println("原SQL: " + selectSql);

        factory.所有字段下划线转驼峰();
        System.out.println("转驼峰: " + factory.toSqlStr());

        factory.所有字段驼峰转下划线();
        System.out.println("转下划线: " + factory.toSqlStr());

        // 测试2: 表名映射
        System.out.println("\n=== 测试2: 表名映射 ===");
        factory.addTableNameMap("user_info", "t_user");
        System.out.println("表名映射后: " + factory.toSqlStr());

        // 测试3: INSERT语句
        System.out.println("\n=== 测试3: INSERT语句 ===");
        try {
            String insertSql = "INSERT INTO user_info (user_id, user_name, create_time) VALUES (1, 'John', '2024-01-01')";
            factory.fromSql(insertSql);
            System.out.println("原SQL: " + insertSql);

            factory.所有字段下划线转驼峰();
            System.out.println("转驼峰: " + factory.toSqlStr());

            // 测试表名映射
            factory.addTableNameMap("user_info", "t_user");
            System.out.println("表名映射后: " + factory.toSqlStr());
        } catch (Exception e) {
            System.err.println("INSERT语句处理失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 测试4: UPDATE语句
        System.out.println("\n=== 测试4: UPDATE语句 ===");
        String updateSql = "UPDATE user_info SET user_name = 'Jane', update_time = '2024-01-02' WHERE user_id = 1";
        factory.fromSql(updateSql);
        System.out.println("原SQL: " + updateSql);

        factory.所有字段下划线转驼峰();
        System.out.println("转驼峰: " + factory.toSqlStr());

        // 测试6: 添加驼峰别名
        System.out.println("\n=== 测试6: 添加驼峰别名 ===");
        String aliasSql = "SELECT user_id, user_name, create_time FROM user_info WHERE user_id = 1";
        factory.fromSql(aliasSql);
        System.out.println("原SQL: " + aliasSql);

        factory.所有查询字段软添加驼峰别名();
        System.out.println("添加驼峰别名: " + factory.toSqlStr());

        // 测试带表别名的情况
        String aliasWithTableSql = "SELECT u.user_id, u.user_name, u.create_time FROM user_info u WHERE u.user_id = 1";
        factory.fromSql(aliasWithTableSql);
        System.out.println("原SQL(带表别名): " + aliasWithTableSql);

        factory.所有查询字段软添加驼峰别名();
        System.out.println("添加驼峰别名(带表别名): " + factory.toSqlStr());

        // 测试已有别名的情况
        String existingAliasSql = "SELECT user_id AS id, user_name, create_time FROM user_info";
        factory.fromSql(existingAliasSql);
        System.out.println("原SQL(已有别名): " + existingAliasSql);

        factory.所有查询字段软添加驼峰别名();
        System.out.println("添加驼峰别名(已有别名): " + factory.toSqlStr());

        // 测试5: 更多INSERT语句场景
        System.out.println("\n=== 测试5: 更多INSERT语句场景 ===");
        String[] insertTests = {
                "INSERT INTO users (id, name) VALUES (1, 'test')",
                "INSERT INTO db.users (user_id, user_name) VALUES (2, 'John Doe')",
                "INSERT INTO user_info (id, name, email) VALUES (3, 'Jane', 'jane@example.com')"
        };

        for (String sql : insertTests) {
            try {
                factory.fromSql(sql);
                System.out.println("原始: " + sql);
                factory.所有字段下划线转驼峰();
                System.out.println("转换: " + factory.toSqlStr());
                System.out.println();
            } catch (Exception e) {
                System.err.println("处理失败: " + sql);
                e.printStackTrace();
            }
        }
    }
}

// 接口定义
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

// 查询字段别名
interface SelectAsName {
    void 所有查询字段软添加驼峰别名();
}