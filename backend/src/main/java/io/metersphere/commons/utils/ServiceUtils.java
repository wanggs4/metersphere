package io.metersphere.commons.utils;

import io.metersphere.base.domain.Project;
import io.metersphere.base.domain.User;
import io.metersphere.commons.exception.MSException;
import io.metersphere.controller.request.BaseQueryRequest;
import io.metersphere.controller.request.OrderRequest;
import io.metersphere.controller.request.ResetOrderRequest;
import io.metersphere.service.ProjectService;
import io.metersphere.service.UserService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ServiceUtils {

    public static final int ORDER_STEP = 5000;

    public static List<OrderRequest> getDefaultOrder(List<OrderRequest> orders) {
        return getDefaultOrder(null, orders);
    }

    public static List<OrderRequest> getDefaultSortOrder(List<OrderRequest> orders) {
        return getDefaultOrderByField(null, orders, "order");
    }

    public static List<OrderRequest> getDefaultSortOrder(String prefix, List<OrderRequest> orders) {
        return getDefaultOrderByField(prefix, orders, "order");
    }

    public static List<OrderRequest> getDefaultOrder(String prefix, List<OrderRequest> orders) {
        return getDefaultOrderByField(prefix, orders, "update_time");
    }

    private static List<OrderRequest> getDefaultOrderByField(String prefix, List<OrderRequest> orders, String field) {
        if (orders == null || orders.size() < 1) {
            OrderRequest orderRequest = new OrderRequest();
            orderRequest.setName(field);
            orderRequest.setType("desc");
            if (StringUtils.isNotBlank(prefix)) {
                orderRequest.setPrefix(prefix);
            }
            orders = new ArrayList<>();
            orders.add(orderRequest);
            return orders;
        }
        return orders;
    }

    public static List<OrderRequest> getDefaultOrderByField(List<OrderRequest> orders, String field) {
        return getDefaultOrderByField(null, orders, field);
    }

    /**
     * ?????????????????????id??????
     *
     * @param queryRequest ????????????
     * @param func         ??????id????????????????????????
     * @return
     */
    public static <T> void getSelectAllIds(T batchRequest, BaseQueryRequest queryRequest, Function<BaseQueryRequest, List<String>> func) {
        if (queryRequest != null && queryRequest.isSelectAll()) {
            List<String> ids = func.apply(queryRequest);
            if (!ids.isEmpty()) {
                ids = ids.stream()
                        .filter(id -> !queryRequest.getUnSelectIds().contains(id))
                        .collect(Collectors.toList());
            }
            queryRequest.setIds(ids);
            try {
                Method setIds = batchRequest.getClass().getDeclaredMethod("setIds", List.class);
                setIds.invoke(batchRequest, ids);
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
                MSException.throwException("????????????setIds??????");
            }
        }
    }

    public static Map<String, User> getUserMap(List<String> userIds) {
        UserService userService = CommonBeanFactory.getBean(UserService.class);
        if (!CollectionUtils.isEmpty(userIds)) {
            Map<String, User> userMap = userService.queryNameByIds(userIds);
            return userMap;
        }
        return new HashMap<>();
    }

    public static Map<String, String> getUserNameMap(List<String> userIds) {
        Map<String, User> userMap = getUserMap(userIds);
        HashMap<String, String> nameMap = new HashMap<>();
        userMap.forEach((k, v) -> {
            nameMap.put(k, v.getName());
        });
        return nameMap;
    }

    public static Map<String, Project> getProjectMap(List<String> ids) {
        ProjectService projectService = CommonBeanFactory.getBean(ProjectService.class);
        if (!CollectionUtils.isEmpty(ids)) {
            Map<String, Project> projectMap = projectService.queryNameByIds(ids);
            return projectMap;
        }
        return new HashMap<>();
    }

    public static Map<String, String> getProjectNameMap(List<String> ids) {
        Map<String, Project> projectMap = getProjectMap(ids);
        HashMap<String, String> nameMap = new HashMap<>();
        projectMap.forEach((k, v) -> {
            nameMap.put(k, v.getName());
        });
        return nameMap;
    }

    /**
     * ????????? order ???
     * @param clazz
     * @param mapClazz
     * @param selectProjectIdsFunc
     * @param getIdsOrderByUpdateTimeFunc
     * @param <T>
     * @param <M>
     */
    public static <T, M> void initOrderField(Class<T> clazz, Class<M> mapClazz,
                                          Supplier<List<String>> selectProjectIdsFunc,
                                          Function<String, List<String>> getIdsOrderByUpdateTimeFunc) {

        try {

            SqlSessionFactory sqlSessionFactory = CommonBeanFactory.getBean(SqlSessionFactory.class);
            Method setId = clazz.getMethod("setId", String.class);
            Method setOrder = clazz.getMethod("setOrder", Long.class);
            SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
            Object mapper = sqlSession.getMapper(mapClazz);

            List<String> projectIds = selectProjectIdsFunc.get();
            for (String projectId : projectIds) {
                Long order = 0L;
                List<String> ids = getIdsOrderByUpdateTimeFunc.apply(projectId);
                for (String id : ids) {
                    T item = clazz.newInstance();
                    setId.invoke(item, id);
                    setOrder.invoke(item, order);
                    order += ServiceUtils.ORDER_STEP;
                    Method updateByPrimaryKeySelectiveFunc = mapper.getClass().getMethod("updateByPrimaryKeySelective", clazz);
                    updateByPrimaryKeySelectiveFunc.invoke(mapper, item);
                }
                sqlSession.flushStatements();
            }
            if (sqlSession != null && sqlSessionFactory != null) {
                SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
            }
        } catch (Throwable e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException("????????? order ????????????");
        }
    }

    /**
     *
     * @param request
     * @param clazz
     * @param selectByPrimaryKeyFunc
     * @param getPreOrderFunc
     * @param getLastOrderFunc
     * @param updateByPrimaryKeySelectiveFuc
     * @param <T>
     */
    public static <T> void updateOrderField(ResetOrderRequest request, Class<T> clazz,
                              Function<String, T> selectByPrimaryKeyFunc,
                              BiFunction<String, Long, Long> getPreOrderFunc,
                              BiFunction<String, Long, Long> getLastOrderFunc,
                              Consumer<T> updateByPrimaryKeySelectiveFuc) {
        Long order;
        Long lastOrPreOrder;
        try {
            Method getOrder = clazz.getMethod("getOrder");
            Method setId = clazz.getMethod("setId", String.class);
            Method setOrder = clazz.getMethod("setOrder", Long.class);

            // ???????????????????????????
            T target = selectByPrimaryKeyFunc.apply(request.getTargetId());
            Long targetOrder = (Long) getOrder.invoke(target);

            if (request.getMoveMode().equals(ResetOrderRequest.MoveMode.AFTER.name())) {
                // ??????????????????????????????
                order = targetOrder - ServiceUtils.ORDER_STEP;
                // ?????????????????????????????????????????? order ????????????order
                lastOrPreOrder = getPreOrderFunc.apply(request.getGroupId(), targetOrder);
            } else {
                // ???????????????
                order = targetOrder + ServiceUtils.ORDER_STEP;
                // ??????????????????????????????????????? order ???????????????order
                lastOrPreOrder = getLastOrderFunc.apply(request.getGroupId(), targetOrder);
            }
            if (lastOrPreOrder != null) {
                // ???????????????????????????????????????????????????
                order = (targetOrder + lastOrPreOrder) / 2;
            }

            // ??????order???
            T updateObj = (T) clazz.newInstance();
            setId.invoke(updateObj, request.getMoveId());
            setOrder.invoke(updateObj, order);
            updateByPrimaryKeySelectiveFuc.accept(updateObj);
        } catch (Throwable e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException("?????? order ????????????");
        }
    }

    /**
     * ???????????????????????? order ???
     * @param groupId
     * @param getLastOrderFunc
     * @return
     */
    public static Long getNextOrder(String groupId, BiFunction<String, Long, Long> getLastOrderFunc) {
        Long lastOrder = getLastOrderFunc.apply(groupId, null);
       return (lastOrder == null ? 0 : lastOrder) + ServiceUtils.ORDER_STEP;
    }

    public static SqlSession getBatchSqlSession() {
        SqlSessionFactory sqlSessionFactory = CommonBeanFactory.getBean(SqlSessionFactory.class);
        return sqlSessionFactory.openSession(ExecutorType.BATCH);
    }
}
