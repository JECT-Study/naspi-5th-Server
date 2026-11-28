package kr.ject.naspi.websocket.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse;
import kr.ject.naspi.websocket.api.response.stock.StockResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

/**
 * 웹소켓 토픽 메시지와 같은 데이터를 내려주는 REST 응답의 필드 이름·타입이 같은지 검사한다. 기준은 REST 응답이다.
 *
 * <p>실패하면 웹소켓 DTO를 REST DTO에 맞춰 고친다 ({@code /sync-topic-spec}). 의도한 차이는 {@link #PAIRS}의
 * {@code topicOnly}·{@code restOnly}에, REST 대응이 없는 토픽은 {@link #WITHOUT_REST}에 등록한다.
 */
class TopicResponseSpecTest {

    private static final String RESPONSE_PACKAGE = "kr.ject.naspi.websocket.api.response";

    private static final List<Pair> PAIRS = List.of(
            new Pair(AssetResponse.class, AssetResponse.class,
                    "kr.ject.naspi.asset.api.me.MyAssetResponse", Set.of("asOf"), Set.of()),
            new Pair(OrderBookResponse.class, OrderBookResponse.class,
                    "kr.ject.naspi.order.api.orderbook.OrderBookResponse", Set.of("asOf"),
                    Set.of()),
            new Pair(StockResponse.class, StockResponse.Stock.class,
                    "kr.ject.naspi.market.api.stock.MemberStockResponse", Set.of(),
                    Set.of("mine")),
            new Pair(LeagueDetailResponse.class, LeagueDetailResponse.class,
                    "kr.ject.naspi.league.api.lobby.LeagueDetailResponse", Set.of("asOf"),
                    Set.of("players[].me")));

    private static final Set<Class<?>> WITHOUT_REST =
            Set.of(CandleChartResponse.class, AlertResponse.class);

    @Test
    @DisplayName("토픽 메시지의 필드 이름·타입은 대응하는 REST 응답과 같다 (허용된 차이 제외)")
    void topicResponsesMatchRestResponses() throws ClassNotFoundException {
        List<String> errors = new ArrayList<>();
        for (Pair pair : PAIRS) {
            Class<?> rest = Class.forName(pair.rest());
            compareRecords(pair.topic(), rest, pair.topicOnly(), pair.restOnly(),
                    pair.topic().getName() + " ↔ " + rest.getName(), errors);
        }

        assertThat(errors)
                .withFailMessage("웹소켓 DTO를 REST DTO에 맞춰야 한다 (/sync-topic-spec):%n%s",
                        String.join(System.lineSeparator(), errors))
                .isEmpty();
    }

    @Test
    @DisplayName("모든 토픽 메시지는 REST 대응이 등록되어 있거나 REST 대응 없음으로 등록되어 있다")
    void everyTopicMessageIsRegistered() {
        Set<Class<?>> registered = PAIRS.stream().map(Pair::message)
                .collect(Collectors.toCollection(HashSet::new));
        registered.addAll(WITHOUT_REST);

        Set<Class<?>> messages = findTopicMessages();

        assertThat(messages)
                .withFailMessage("PAIRS 또는 WITHOUT_REST 에 등록하지 않은 토픽 메시지: %s",
                        messages.stream().filter(c -> !registered.contains(c)).toList())
                .isNotEmpty()
                .isSubsetOf(registered);
    }

    @Test
    @DisplayName("비교기는 이름·타입·nullable·중첩 record·목록 원소 차이를 찾고, 제외한 경로는 중첩 안에서도 건너뛴다")
    void comparatorDetectsDifferences() {
        List<String> errors = new ArrayList<>();

        compareRecords(TopicSample.class, RestSample.class, Set.of("asOf"),
                Set.of("items[].viewerOnly"), "sample", errors);

        assertThat(errors).containsExactlyInAnyOrder(
                "sample.price: 타입이 다르다 (토픽 java.lang.Long, REST long)",
                "sample.renamed: 토픽에 없다 (REST long)",
                "sample.oldName: REST에 없다 (토픽 long)",
                "sample.items[].qty: 타입이 다르다 (토픽 int, REST long)");
    }

    private static void compareRecords(Class<?> topic, Class<?> rest, Set<String> topicOnly,
            Set<String> restOnly, String label, List<String> errors) {
        compareRecords(topic, rest, new Context(label, topicOnly, restOnly, errors), "");
    }

    /**
     * record 필드를 비교한다.
     *
     * @param path 최상위 record 기준 경로. 최상위는 빈 문자열, 목록 원소는 {@code players[]}처럼 쓴다
     */
    private static void compareRecords(Class<?> topic, Class<?> rest, Context context,
            String path) {
        Map<String, Type> topicFields = components(topic, context.topicOnly(), path);
        Map<String, Type> restFields = components(rest, context.restOnly(), path);
        restFields.forEach((field, restType) -> {
            Type topicType = topicFields.get(field);
            if (topicType == null) {
                context.error(field, "토픽에 없다 (REST " + restType.getTypeName() + ")");
            } else {
                compareTypes(topicType, restType, context, field);
            }
        });
        topicFields.forEach((field, topicType) -> {
            if (!restFields.containsKey(field)) {
                context.error(field, "REST에 없다 (토픽 " + topicType.getTypeName() + ")");
            }
        });
    }

    private static void compareTypes(Type topic, Type rest, Context context, String path) {
        if (topic instanceof Class<?> topicClass && rest instanceof Class<?> restClass
                && topicClass.isRecord() && restClass.isRecord()) {
            compareRecords(topicClass, restClass, context, path);
            return;
        }
        if (topic instanceof ParameterizedType topicParam
                && rest instanceof ParameterizedType restParam
                && topicParam.getRawType().equals(restParam.getRawType())) {
            Type[] topicArgs = topicParam.getActualTypeArguments();
            Type[] restArgs = restParam.getActualTypeArguments();
            for (int i = 0; i < topicArgs.length; i++) {
                compareTypes(topicArgs[i], restArgs[i], context, path + "[]");
            }
            return;
        }
        if (!topic.equals(rest)) {
            context.error(path, "타입이 다르다 (토픽 " + topic.getTypeName() + ", REST "
                    + rest.getTypeName() + ")");
        }
    }

    /**
     * record 필드를 경로를 키로 모은다. 제외 목록에 있는 경로는 뺀다.
     */
    private static Map<String, Type> components(Class<?> type, Set<String> excluded,
            String path) {
        Map<String, Type> fields = new LinkedHashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            String field = path.isEmpty() ? component.getName() : path + "." + component.getName();
            if (!excluded.contains(field)) {
                fields.put(field, component.getGenericType());
            }
        }
        return fields;
    }

    private static Set<Class<?>> findTopicMessages() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        return scanner.findCandidateComponents(RESPONSE_PACKAGE).stream()
                .map(definition -> load(definition.getBeanClassName()))
                .filter(TopicResponseSpecTest::hasTypeConstant)
                .collect(Collectors.toSet());
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasTypeConstant(Class<?> type) {
        try {
            type.getDeclaredField("TYPE");
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * 비교 중인 쌍의 정보와 오류 목록.
     *
     * @param label 오류 메시지 앞에 붙일 이름
     * @param topicOnly 토픽에만 있는 필드 경로
     * @param restOnly REST에만 있는 필드 경로
     * @param errors 오류 목록
     */
    private record Context(String label, Set<String> topicOnly, Set<String> restOnly,
            List<String> errors) {

        void error(String path, String message) {
            errors.add(label + "." + path + ": " + message);
        }
    }

    /**
     * 토픽 메시지와 비교할 REST 응답.
     *
     * @param message 토픽 메시지 ({@code TYPE} 상수를 가진 클래스)
     * @param topic 비교할 토픽 쪽 record. 메시지가 목록을 감싸면 그 원소
     * @param rest REST 응답 클래스 이름 (package-private 이라 이름으로 참조)
     * @param topicOnly 토픽에만 있는 필드 경로 (예: {@code asOf})
     * @param restOnly REST에만 있는 필드 경로 (예: 조회한 회원 기준 값인 {@code mine}, 목록 원소 안이면
     *     {@code players[].me})
     */
    private record Pair(Class<?> message, Class<?> topic, String rest, Set<String> topicOnly,
            Set<String> restOnly) {
    }

    private record TopicSample(long id, Long price, long oldName, List<Item> items, String asOf) {

        private record Item(int qty) {
        }
    }

    private record RestSample(long id, long price, long renamed, List<Item> items) {

        private record Item(long qty, boolean viewerOnly) {
        }
    }
}
