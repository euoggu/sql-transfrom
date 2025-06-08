package com.example.testar;

import java.util.*;

// RestBody主类
class RestBody {
    private String fiscalYear;
    private String mofDivCode;
    private QueryDTO queryDTO;
    
    // Getters and Setters
    public String getFiscalYear() {
        return fiscalYear;
    }
    
    public void setFiscalYear(String fiscalYear) {
        this.fiscalYear = fiscalYear;
    }
    
    public String getMofDivCode() {
        return mofDivCode;
    }
    
    public void setMofDivCode(String mofDivCode) {
        this.mofDivCode = mofDivCode;
    }
    
    public QueryDTO getQueryDTO() {
        return queryDTO;
    }
    
    public void setQueryDTO(QueryDTO queryDTO) {
        this.queryDTO = queryDTO;
    }
}

// 查询DTO类
class QueryDTO {
    private Page page;
    private List<String> fieldNames;
    private List<Map<String, Object>> whereSql;
    private String isRelatedSubTable;
    
    // Getters and Setters
    public Page getPage() {
        return page;
    }
    
    public void setPage(Page page) {
        this.page = page;
    }
    
    public List<String> getFieldNames() {
        return fieldNames;
    }
    
    public void setFieldNames(List<String> fieldNames) {
        this.fieldNames = fieldNames;
    }
    
    public List<Map<String, Object>> getWhereSql() {
        return whereSql;
    }
    
    public void setWhereSql(List<Map<String, Object>> whereSql) {
        this.whereSql = whereSql;
    }
    
    public String getIsRelatedSubTable() {
        return isRelatedSubTable;
    }
    
    public void setIsRelatedSubTable(String isRelatedSubTable) {
        this.isRelatedSubTable = isRelatedSubTable;
    }
}

// 分页类
class Page {
    private String pageNumber;
    private String pageSize;
    
    // Getters and Setters
    public String getPageNumber() {
        return pageNumber;
    }
    
    public void setPageNumber(String pageNumber) {
        this.pageNumber = pageNumber;
    }
    
    public String getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }
}

// SQL转换工具类
public class RestBodyToSqlUtils {
    
    /**
     * 将RestBody转换为SQL WHERE子句
     * @param restBody 请求体
     * @return SQL WHERE子句字符串（包含WHERE条件和分页）
     */
    public static String trans(RestBody restBody) {
        if (restBody == null) {
            return "";
        }
        
        StringBuilder sql = new StringBuilder();
        List<String> conditions = new ArrayList<>();
        
        // 添加fiscalYear条件
        if (restBody.getFiscalYear() != null && !restBody.getFiscalYear().trim().isEmpty()) {
            conditions.add("fiscal_year='" + restBody.getFiscalYear() + "'");
        }
        
        // 添加mofDivCode条件
        if (restBody.getMofDivCode() != null && !restBody.getMofDivCode().trim().isEmpty()) {
            conditions.add("mof_div_code='" + restBody.getMofDivCode() + "'");
        }
        
        // 处理whereSql条件
        if (restBody.getQueryDTO() != null) {
            List<Map<String, Object>> whereSql = restBody.getQueryDTO().getWhereSql();
            if (whereSql != null && !whereSql.isEmpty()) {
                StringBuilder whereSqlStr = new StringBuilder();
                
                // 处理whereSql数组，数组中的每个元素之间用AND连接
                for (int i = 0; i < whereSql.size(); i++) {
                    if (i > 0) {
                        whereSqlStr.append(" and ");
                    }
                    whereSqlStr.append(parseCondition(whereSql.get(i)));
                }
                
                if (whereSqlStr.length() > 0) {
                    conditions.add(whereSqlStr.toString());
                }
            }
        }
        
        // 组合所有WHERE条件
        if (!conditions.isEmpty()) {
            sql.append("WHERE ");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                sql.append(conditions.get(i));
            }
        }
        
        // 添加分页条件
        if (restBody.getQueryDTO() != null && restBody.getQueryDTO().getPage() != null) {
            Page page = restBody.getQueryDTO().getPage();
            if (page.getPageSize() != null && page.getPageNumber() != null) {
                try {
                    int pageSize = Integer.parseInt(page.getPageSize());
                    int pageNumber = Integer.parseInt(page.getPageNumber());
                    int offset = (pageNumber - 1) * pageSize;
                    
                    if (sql.length() > 0) {
                        sql.append(" ");
                    }
                    sql.append("LIMIT ").append(pageSize)
                       .append(" OFFSET ").append(offset);
                } catch (NumberFormatException e) {
                    // 如果解析失败，忽略分页
                }
            }
        }
        
        return sql.toString();
    }
    
    /**
     * 递归解析条件
     * @param condition 条件Map
     * @return 解析后的SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseCondition(Map<String, Object> condition) {
        StringBuilder result = new StringBuilder();
        
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            switch (key.toLowerCase()) {
                case "and":
                    result.append(parseAndOr(value, " and "));
                    break;
                case "or":
                    result.append(parseAndOr(value, " or "));
                    break;
                case "bracket":
                    result.append(parseBracket(value));
                    break;
                case "equal":
                    result.append(parseEqual(value));
                    break;
                case "like":
                    result.append(parseLike(value));
                    break;
                case "greater":
                    result.append(parseGreater(value));
                    break;
                case "less":
                    result.append(parseLess(value));
                    break;
                case "not_equal":
                    result.append(parseNotEqual(value));
                    break;
                case "in":
                    result.append(parseIn(value));
                    break;
                case "not_in":
                    result.append(parseNotIn(value));
                    break;
                default:
                    // 处理其他未定义的操作符
                    break;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 解析AND/OR条件
     * @param value 条件值
     * @param operator 操作符 (and/or)
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseAndOr(Object value, String operator) {
        if (value instanceof Map) {
            return parseCondition((Map<String, Object>) value);
        } else if (value instanceof List) {
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    result.append(operator);
                }
                result.append(parseCondition(conditions.get(i)));
            }
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析括号条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseBracket(Object value) {
        if (value instanceof List) {
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) value;
            StringBuilder result = new StringBuilder("(");
            
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    // 判断当前条件是否包含逻辑操作符
                    Map<String, Object> currentCondition = conditions.get(i);
                    boolean hasOr = currentCondition.containsKey("or");
                    boolean hasAnd = currentCondition.containsKey("and");
                    
                    if (hasOr) {
                        result.append(" or ");
                    } else if (hasAnd) {
                        result.append(" and ");
                    } else {
                        // 如果没有明确指定操作符，默认使用or
                        result.append(" or ");
                    }
                }
                
                // 解析当前条件
                String conditionStr = parseCondition(conditions.get(i));
                result.append(conditionStr);
            }
            
            result.append(")");
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析等于条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseEqual(Object value) {
        if (value instanceof Map) {
            Map<String, Object> equalMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : equalMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append("='")
                      .append(entry.getValue())
                      .append("'");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析LIKE条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseLike(Object value) {
        if (value instanceof Map) {
            Map<String, Object> likeMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : likeMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append(" like '")
                      .append(entry.getValue())
                      .append("'");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析大于条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseGreater(Object value) {
        if (value instanceof Map) {
            Map<String, Object> greaterMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : greaterMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append(">'")
                      .append(entry.getValue())
                      .append("'");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析小于条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseLess(Object value) {
        if (value instanceof Map) {
            Map<String, Object> lessMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : lessMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append("<'")
                      .append(entry.getValue())
                      .append("'");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析不等于条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseNotEqual(Object value) {
        if (value instanceof Map) {
            Map<String, Object> notEqualMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : notEqualMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append("!='")
                      .append(entry.getValue())
                      .append("'");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析IN条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseIn(Object value) {
        if (value instanceof Map) {
            Map<String, Object> inMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : inMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append(" in (");
                
                Object inValue = entry.getValue();
                if (inValue instanceof List) {
                    List<?> valueList = (List<?>) inValue;
                    for (int i = 0; i < valueList.size(); i++) {
                        if (i > 0) {
                            result.append(",");
                        }
                        result.append("'").append(valueList.get(i)).append("'");
                    }
                } else {
                    result.append("'").append(inValue).append("'");
                }
                
                result.append(")");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
    
    /**
     * 解析NOT IN条件
     * @param value 条件值
     * @return SQL片段
     */
    @SuppressWarnings("unchecked")
    private static String parseNotIn(Object value) {
        if (value instanceof Map) {
            Map<String, Object> notInMap = (Map<String, Object>) value;
            StringBuilder result = new StringBuilder();
            int count = 0;
            
            for (Map.Entry<String, Object> entry : notInMap.entrySet()) {
                if (count > 0) {
                    result.append(" and ");
                }
                result.append(entry.getKey())
                      .append(" not in (");
                
                Object notInValue = entry.getValue();
                if (notInValue instanceof List) {
                    List<?> valueList = (List<?>) notInValue;
                    for (int i = 0; i < valueList.size(); i++) {
                        if (i > 0) {
                            result.append(",");
                        }
                        result.append("'").append(valueList.get(i)).append("'");
                    }
                } else {
                    result.append("'").append(notInValue).append("'");
                }
                
                result.append(")");
                count++;
            }
            
            return result.toString();
        }
        return "";
    }
}

// 测试类
class RestBodyToSqlUtilsTest {
    
    public static void main(String[] args) {
        // 测试用例1：基础的AND和bracket条件
        testCase1();
        
        // 测试用例2：包含OR和LIKE条件
        testCase2();
        
        // 测试用例3：复杂嵌套条件
        testCase3();
        
        // 测试用例4：测试分页功能
        testCase4();
    }
    
    /**
     * 测试用例1：基础的AND和bracket条件
     * 对应你提供的第一个JSON示例
     */
    private static void testCase1() {
        System.out.println("=== 测试用例1 ===");
        
        RestBody restBody = new RestBody();
        restBody.setFiscalYear("2025");
        restBody.setMofDivCode("360000000");
        
        QueryDTO queryDTO = new QueryDTO();
        Page page = new Page();
        page.setPageNumber("1");
        page.setPageSize("50");
        queryDTO.setPage(page);
        queryDTO.setFieldNames(new ArrayList<>());
        
        // 构建whereSql
        List<Map<String, Object>> whereSql = new ArrayList<>();
        
        // 第一个条件：and { bracket: [...] }
        Map<String, Object> condition1 = new HashMap<>();
        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> bracketList = new ArrayList<>();
        
        // bracket中的第一个条件
        Map<String, Object> bracketItem1 = new HashMap<>();
        Map<String, Object> equal1 = new HashMap<>();
        equal1.put("agency_id", "1C8C439A0C2D9F0EE06400144FF80B78");
        bracketItem1.put("equal", equal1);
        bracketList.add(bracketItem1);
        
        // bracket中的第二个条件
        Map<String, Object> bracketItem2 = new HashMap<>();
        Map<String, Object> equal2 = new HashMap<>();
        equal2.put("biz_key", "EA34E11E0B647DA3B1C1E3176D8DE2511");
        bracketItem2.put("equal", equal2);
        bracketList.add(bracketItem2);
        
        andCondition.put("bracket", bracketList);
        condition1.put("and", andCondition);
        whereSql.add(condition1);
        
        queryDTO.setWhereSql(whereSql);
        restBody.setQueryDTO(queryDTO);
        
        String sql = RestBodyToSqlUtils.trans(restBody);
        System.out.println("生成的SQL: " + sql);
        System.out.println("预期的SQL: WHERE fiscal_year='2025' AND mof_div_code='360000000' AND (agency_id='1C8C439A0C2D9F0EE06400144FF80B78' or biz_key='EA34E11E0B647DA3B1C1E3176D8DE2511') LIMIT 50 OFFSET 0");
        System.out.println();
    }
    
    /**
     * 测试用例2：包含OR和LIKE条件
     * 对应你提供的第二个示例
     */
    private static void testCase2() {
        System.out.println("=== 测试用例2 ===");
        
        RestBody restBody = new RestBody();
        restBody.setFiscalYear("2025");
        restBody.setMofDivCode("360000000");
        
        QueryDTO queryDTO = new QueryDTO();
        Page page = new Page();
        page.setPageNumber("1");
        page.setPageSize("50");
        queryDTO.setPage(page);
        queryDTO.setIsRelatedSubTable("false");
        
        List<String> fieldNames = Arrays.asList("agency_id", "agency_code");
        queryDTO.setFieldNames(fieldNames);
        
        // 构建whereSql
        List<Map<String, Object>> whereSql = new ArrayList<>();
        
        // 第一个条件：and { bracket: [...] }
        Map<String, Object> condition1 = new HashMap<>();
        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> bracketList = new ArrayList<>();
        
        // bracket中的第一个条件：equal
        Map<String, Object> bracketItem1 = new HashMap<>();
        Map<String, Object> equal1 = new HashMap<>();
        equal1.put("agency_id", "101001");
        bracketItem1.put("equal", equal1);
        bracketList.add(bracketItem1);
        
        // bracket中的第二个条件：or { greater: {...} }
        Map<String, Object> bracketItem2 = new HashMap<>();
        Map<String, Object> orCondition = new HashMap<>();
        Map<String, Object> greater = new HashMap<>();
        greater.put("to_char(update_time,'yyyymmdd')", "20250101");
        orCondition.put("greater", greater);
        bracketItem2.put("or", orCondition);
        bracketList.add(bracketItem2);
        
        andCondition.put("bracket", bracketList);
        condition1.put("and", andCondition);
        whereSql.add(condition1);
        
        // 第二个条件：and { like: {...} }
        Map<String, Object> condition2 = new HashMap<>();
        Map<String, Object> andCondition2 = new HashMap<>();
        Map<String, Object> like = new HashMap<>();
        like.put("agency_id", "%123%");
        andCondition2.put("like", like);
        condition2.put("and", andCondition2);
        whereSql.add(condition2);
        
        queryDTO.setWhereSql(whereSql);
        restBody.setQueryDTO(queryDTO);
        
        String sql = RestBodyToSqlUtils.trans(restBody);
        System.out.println("生成的SQL: " + sql);
        System.out.println("预期的SQL: WHERE fiscal_year='2025' AND mof_div_code='360000000' AND (agency_id='101001' or to_char(update_time,'yyyymmdd')>'20250101') and agency_id like '%123%' LIMIT 50 OFFSET 0");
        System.out.println();
    }
    
    /**
     * 测试用例3：更复杂的嵌套条件
     */
    private static void testCase3() {
        System.out.println("=== 测试用例3：复杂嵌套条件 ===");
        
        RestBody restBody = new RestBody();
        restBody.setFiscalYear("2025");
        restBody.setMofDivCode("360000000");
        
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setFieldNames(new ArrayList<>());
        
        // 构建复杂的whereSql
        List<Map<String, Object>> whereSql = new ArrayList<>();
        
        // 第一个条件：包含多个操作符
        Map<String, Object> condition1 = new HashMap<>();
        Map<String, Object> andCondition = new HashMap<>();
        
        // 创建一个包含equal和like的Map
        Map<String, Object> multiConditions = new HashMap<>();
        
        Map<String, Object> equal = new HashMap<>();
        equal.put("status", "active");
        multiConditions.put("equal", equal);
        
        andCondition.putAll(multiConditions);
        condition1.put("and", andCondition);
        whereSql.add(condition1);
        
        // 第二个条件：OR条件
        Map<String, Object> condition2 = new HashMap<>();
        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> bracketList = new ArrayList<>();
        
        Map<String, Object> item1 = new HashMap<>();
        Map<String, Object> greater = new HashMap<>();
        greater.put("amount", "1000");
        item1.put("greater", greater);
        bracketList.add(item1);
        
        Map<String, Object> item2 = new HashMap<>();
        Map<String, Object> equal2 = new HashMap<>();
        equal2.put("type", "VIP");
        item2.put("equal", equal2);
        bracketList.add(item2);
        
        orCondition.put("bracket", bracketList);
        condition2.put("or", orCondition);
        whereSql.add(condition2);
        
        queryDTO.setWhereSql(whereSql);
        restBody.setQueryDTO(queryDTO);
        
        String sql = RestBodyToSqlUtils.trans(restBody);
        System.out.println("生成的SQL: " + sql);
        System.out.println();
    }
    
    /**
     * 测试用例4：测试分页功能
     */
    private static void testCase4() {
        System.out.println("=== 测试用例4：测试分页功能 ===");
        
        RestBody restBody = new RestBody();
        restBody.setFiscalYear("2025");
        restBody.setMofDivCode("360000000");
        
        QueryDTO queryDTO = new QueryDTO();
        Page page = new Page();
        page.setPageNumber("3");
        page.setPageSize("20");
        queryDTO.setPage(page);
        
        restBody.setQueryDTO(queryDTO);
        
        String sql = RestBodyToSqlUtils.trans(restBody);
        System.out.println("生成的SQL: " + sql);
        System.out.println("预期的SQL: WHERE fiscal_year='2025' AND mof_div_code='360000000' LIMIT 20 OFFSET 40");
        System.out.println();
    }
}