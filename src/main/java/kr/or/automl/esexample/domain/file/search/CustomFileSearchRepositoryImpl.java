package kr.or.automl.esexample.domain.file.search;

import kr.or.automl.esexample.domain.file.File;
import kr.or.automl.esexample.domain.file.Meta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CustomFileSearchRepositoryImpl implements CustomFileSearchRepository {
    private static final List<String> FIELDS = new ArrayList<>();

    static {
        FIELDS.addAll(getFileFields());
        FIELDS.addAll(getFileEmbeddedFields());
    }

    private final ElasticsearchOperations elasticsearchOperations;

    public CustomFileSearchRepositoryImpl(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    private static List<String> getFileFields() {
        return Arrays.stream(File.class.getDeclaredFields())
                .map(Field::getName)
                .filter(it -> !isId(it) && !isFileEmbeddedClassName(it))
                .collect(Collectors.toList());
    }

    private static boolean isFileEmbeddedClassName(String it) {
        return getFileEmbeddedClassName().equals(it);
    }

    private static boolean isId(String it) {
        return "id".equals(it);
    }

    private static List<String> getFileEmbeddedFields() {
        String className = getFileEmbeddedClassName();

        return Arrays.stream(Meta.class.getDeclaredFields())
                .map(Field::getName)
                .map(it -> String.format("%s.%s", className, it))
                .collect(Collectors.toList());
    }

    private static String getFileEmbeddedClassName() {
        return Meta.class.getSimpleName().toLowerCase();
    }

    @Override
    public List<File> search(String keyword, Pageable pageable) {
        List<Query> queries = getQueries(keyword, pageable);

        List<SearchHits<File>> searchHits = elasticsearchOperations.multiSearch(queries, File.class);

        return searchHits.stream()
                .filter(this::hasTotalHits)
                .flatMap(Streamable::stream)
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private boolean hasTotalHits(SearchHits<File> it) {
        return it.getTotalHits() > 0;
    }

    private List<Query> getQueries(String keyword, Pageable pageable) {
        return FIELDS.stream()
                .map(field -> createCriteria(field, keyword))
                .map(criteria -> createCriteriaQuery(criteria, pageable))
                .collect(Collectors.toList());
    }

    private Criteria createCriteria(String field, String keyword) {
        return new Criteria(field).contains(keyword);
    }

    private Query createCriteriaQuery(Criteria criteria, Pageable pageable) {
        return new CriteriaQuery(criteria).setPageable(pageable);
    }

}
