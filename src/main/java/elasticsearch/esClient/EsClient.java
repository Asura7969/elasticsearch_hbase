package elasticsearch.esClient;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.mustache.SearchTemplateRequestBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * 汽车零售案例背景
 * 简单来说，会涉及到三个数据，汽车信息，汽车销售记录，汽车4S店信息
 */
public class EsClient {
    public static void main(String[] args) throws Exception {

        Settings settings = Settings.builder()
                //集群名字
                .put("cluster.name", "elasticsearch")
                //开启集群节点自动探查功能
                .put("client.transport.sniff", true)
                .build();
        //获取客户端
        TransportClient client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost1"), 9300));

        /**
         * 首先的话呢，第一次调整宝马320这个汽车的售价，我们希望将售价设置为32万，用一个upsert语法，
         * 如果这个汽车的信息之前不存在，那么就insert，如果存在，那么就update
         * 附：esData.txt (1)
         */
        //建立索引
        IndexRequest indexRequest = new IndexRequest("car_shop", "cars", "1")
                .source(jsonBuilder()
                        .startObject()
                        .field("brand", "宝马")
                        .field("name", "宝马320")
                        .field("price", 320000)
                        .field("produce_date", "2017-01-01")
                        .endObject());

        //与建立索引做关联
        UpdateRequest updateRequest = new UpdateRequest("car_shop", "cars", "1")
                .doc(jsonBuilder()
                        .startObject()
                        .field("price", 320000)
                        .endObject())
                .upsert(indexRequest);

        UpdateResponse updateResponse = client.update(updateRequest).get();

        System.out.println(updateResponse.getGetResult().getVersion());


        /************************************************mget***********************************************/
        //附：esData.txt (2)
        MultiGetResponse multiGetItemResponses = client.prepareMultiGet()
                .add("car_shop", "cars", "1")
                .add("car_shop", "cars", "2")
                .get();

        for (MultiGetItemResponse itemResponse : multiGetItemResponses) {
            GetResponse response = itemResponse.getResponse();
            //如果结果存在
            if (response.isExists()) {
                String json = response.getSourceAsString();
                System.out.println(json);
            }
        }

        /*******************************************批量操作增、删、改******************************************/
        //附：esData.txt (3)

        BulkRequestBuilder bulkRequest = client.prepareBulk();
        //新增数据操作
        bulkRequest.add(client.prepareIndex("car_shop", "sales", "3")
                .setSource(jsonBuilder()
                        .startObject()
                        .field("brand", "奔驰")
                        .field("name", "奔驰C200")
                        .field("price", 350000)
                        .field("produce_date", "2017-01-05")
                        .field("sale_price", 340000)
                        .field("sale_date", "2017-02-03")
                        .endObject()
                )
        );
        //修改操作
        bulkRequest.add(client.prepareUpdate("car_shop", "sales", "1")
                .setDoc(jsonBuilder()
                        .startObject()
                        .field("sale_price", "290000")
                        .endObject()
                )
        );
        //删除操作
        bulkRequest.add(client.prepareDelete("car_shop", "sales", "2"));

        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            for (BulkItemResponse bulkItemResponse : bulkResponse.getItems()) {
                System.out.println("version:" + bulkItemResponse.getVersion());
            }
        }

        /*******************************************批量查询******************************************/
        //附：esData.txt (4)

        SearchResponse scrollResp = client.prepareSearch("car_shop")
                //设置index和Type
                .setTypes("sales")
                //打开连接
                .setScroll(new TimeValue(60000))
                .setQuery(termQuery("brand.raw", "宝马"))
                .setSize(1)
                .get();
        int batchCount = 1;
        do {
            for (SearchHit hit : scrollResp.getHits().getHits()) {
                System.out.println("batch : " + ++batchCount);
                System.out.println(hit.getSourceAsString());

                //每次查询一批数据，比如1000行数据，再做操作
                //如果说一下子查询几千万行数据做操作不现实
            }
            scrollResp = client.prepareSearchScroll(scrollResp.getScrollId())
                    .setScroll(new TimeValue(60000))
                    .execute()
                    .actionGet();
        } while(scrollResp.getHits().getHits().length != 0);

        /*******************************************分页模板查询******************************************/
        //附：esData.txt (5)
        Map<String,Object> template_params = new HashMap<String, Object>();
        template_params.put("from",0);
        template_params.put("size",1);
        template_params.put("brand","宝马");

        SearchResponse sr = new SearchTemplateRequestBuilder(client)
                .setScript("page_query_by_brand")
                //.setScriptType(ScriptType.FILE)
                .setScriptParams(template_params)
                .setRequest(new SearchRequest("car_shop").types("sales"))
                .get()
                .getResponse();
        for(SearchHit searchHit : sr.getHits().getHits()) {
            System.out.println(searchHit.getSourceAsString());
        }


        /*********************************全文检索,精准查询,前缀搜索*************************************/
        //附：esData.txt (6)
        //全文检索
        SearchResponse res0 = client.prepareSearch("car_shop")
                .setTypes("cars")
                .setQuery(QueryBuilders.matchQuery("brand", "宝马"))
                .get();
        //多个字段查询
        SearchResponse res1 = client.prepareSearch("car_shop")
                .setTypes("cars")
                .setQuery(QueryBuilders.multiMatchQuery("宝马", "brand", "name"))
                .get();
        //精准查询
        SearchResponse res2 = client.prepareSearch("car_shop")
                .setTypes("cars")
                .setQuery(QueryBuilders.commonTermsQuery("name", "宝马320"))
                .get();
        //前缀搜索
        SearchResponse res3 = client.prepareSearch("car_shop")
                .setTypes("cars")
                .setQuery(QueryBuilders.prefixQuery("name", "宝"))
                .get();

        for (SearchHit searchHitFields : res0.getHits().getHits()) {
            System.out.println(searchHitFields.getSourceAsString());
        }











        client.close();

    }
}
