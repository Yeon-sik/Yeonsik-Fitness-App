package com.yeonsik.fitnessapp.data;

import org.junit.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class BrandReadV1Test {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private Map<String, Object> row(Object id, Object name, Object alias) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("brand_id", id); row.put("canonical_name", name); row.put("matched_alias", alias);
        return row;
    }
    @Test public void preservesCanonicalIdentityAndAliasWhileDroppingMalformedRows() {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("contract_version", BrandReadV1.CONTRACT_VERSION);
        payload.put("brands", Arrays.asList(row(ID,"농심","Nongshim"), row("1-1-1-1-1","오류",null),
                row(ID,"  ",null), row(ID,7,null), row(ID,"잘못된 별칭",7), row(ID,"오뚜기",null), "invalid"));
        java.util.List<BrandReadV1> result = BrandReadV1.fromContractMap(payload);
        assertEquals(2,result.size()); assertEquals(ID,result.get(0).brandId);
        assertEquals("농심",result.get(0).canonicalName); assertEquals("Nongshim",result.get(0).matchedAlias);
        assertNull(result.get(1).matchedAlias);
    }
    @Test public void rejectsWrongContractAndMissingList() {
        assertThrows(IllegalArgumentException.class, () -> BrandReadV1.fromContractMap(Map.of("contract_version","brand_read.v2","brands",java.util.List.of())));
        assertThrows(IllegalArgumentException.class, () -> BrandReadV1.fromContractMap(Map.of("contract_version",BrandReadV1.CONTRACT_VERSION)));
    }
}
