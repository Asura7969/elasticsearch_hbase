package com.sxt.es.test;

import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;

/**
 * Created by root on 2016/3/26 0026.
 */
public class Testes {
    public static void main(String[] args) throws Exception {
        Settings settings = Settings.settingsBuilder()
                .put("cluster.name", "chenkl").build();
        Client client = TransportClient.builder().settings(settings).build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("master"), 9300))
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("slave1"), 9300))
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("slave2"), 9300));


        XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .field("user", "123456")
                .field("postDate", new Date())
                .field("message", "trying out Elasticsearch!!!!!!!!!!!!!!")
                .endObject();
        String json = builder.string();
        //建立索引
//        IndexResponse response = client.prepareIndex("bjsxt", "emp", "2").setSource(json).execute().actionGet();
        //查询索引两种方式
//        GetResponse response = client.prepareGet("bjsxt", "emp", "1").execute().actionGet();
//        GetResponse response = client.prepareGet("bjsxt", "emp", "1").get();
//        Map<String, Object> source = response.getSource();
//        for(Map.Entry<String,Object> filed :source.entrySet()){
//            String key = filed.getKey();
//            System.out.println("key===="+key+"    name====="+filed.getValue().toString());
//        }
        //修改 update
//        UpdateRequest updateRequest = new UpdateRequest();
//        updateRequest.index("bjsxt");
//        updateRequest.type("emp");
//        updateRequest.id("1");
//        updateRequest.doc(builder);
//        client.update(updateRequest).get();
//删除索引
//        DeleteResponse response = client.prepareDelete("bjsxt", "emp", "1").get();
//搜索api
        SearchResponse response = client.prepareSearch("bjsxt")
                .setTypes("emp")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                // Query
                .setQuery(QueryBuilders.termQuery("user", "123456"))
                // Filter
//                .setPostFilter(QueryBuilders.rangeQuery("age").from(12).to(18))
                .setFrom(0).setSize(1).setExplain(true)
                .execute()
                .actionGet();

        SearchHits hits = response.getHits();
        System.out.println(hits.getHits().length);
        SearchHit[] hits1 = hits.getHits();
        for(SearchHit hit :hits1){
            Map<String, Object> source = hit.getSource();
            for(Map.Entry<String,Object> filed :source.entrySet()){
                String key = filed.getKey();
                System.out.println("key===="+key+"    value====="+filed.getValue().toString());
            }
        }
    }
}
