package com.yeonsik.fitnessapp.ui;

import android.app.Dialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.ProductNutritionLink;
import com.yeonsik.fitnessapp.data.ProductReadV1;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Explicit selection, suggestion approval, and unlink UI for cross-project product links. */
final class ProductNutritionLinkDialogController {
    private final ScreenHost host;
    private final FitnessUi ui;
    private final FormSystem forms;
    private final NutritionCatalogRepository repository;
    private Dialog activeDialog;
    private boolean publicationUpdating;

    ProductNutritionLinkDialogController(ScreenHost host) {
        this.host = host;
        this.ui = host.ui();
        this.forms = new FormSystem(ui, host.activity());
        this.repository = host.nutritionCatalogRepository();
    }

    void show(NutritionFood food) {
        dismissActiveDialog();
        LinearLayout body = forms.column();
        body.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16));
        ProductNutritionLink approved = repository.approvedProductLink(food.id);
        List<ProductNutritionLink> suggestions = repository.pendingProductLinkSuggestions(food.id);
        boolean isPublic = repository.isFoodPublic(food.id);
        boolean priceTraceConfigured = host.priceTraceSupabaseConfig().isConnectionConfigured();

        body.addView(ui.text(
                "영양 정보는 이 연결 없이도 독립적으로 저장됩니다. 이름 자동 매칭은 하지 "
                        + "않으며 아래 표준상품 후보에서 연결할 항목을 직접 선택해야 합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        body.addView(ui.keyValue(
                "공개 상태",
                isPublic ? "PriceTrace 공개" : "개인 영양정보"
        ));

        if (approved == null) {
            body.addView(ui.keyValue("현재 연결", "없음 (영양 단독 항목)"));
        } else {
            body.addView(forms.sectionTitle("현재 승인 연결"), ui.fullWidthParams(0));
            body.addView(ui.text(approved.displayLabel(), 12, FitnessUi.COLOR_TEXT, false));
            Button refresh = ui.secondaryButton("상품·가격 새로고침",
                    v -> refreshApprovedProduct(food, approved));
            forms.disabled(refresh, !priceTraceConfigured);
            Button unlink = ui.button("PriceTrace 연결 해제", false,
                    v -> confirmUnlink(food, approved));
            body.addView(ui.buttonRow(refresh, unlink), ui.fullWidthParams(ui.dp(10)));
            Button publication = ui.button(
                    isPublic ? "PriceTrace 공개 취소" : "PriceTrace에 공개",
                    !isPublic,
                    v -> confirmPublication(food, approved, !isPublic)
            );
            body.addView(publication, ui.fullWidthParams(ui.dp(8)));
            body.addView(ui.text(
                    isPublic
                            ? "공개 중에는 영양값과 상품 연결을 변경할 수 없습니다. 먼저 공개를 취소하세요."
                            : "공개하면 이 영양정보가 PriceTrace의 상품 정보 화면에 표시됩니다.",
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
        }

        if (!suggestions.isEmpty()) {
            body.addView(forms.sectionTitle("PriceTrace 제안"), ui.fullWidthParams(ui.dp(4)));
            for (ProductNutritionLink suggestion : suggestions) {
                Button review = ui.button(
                        "제안 검토 · " + suggestion.displayLabel(),
                        false,
                        v -> loadSuggestion(food, suggestion)
                );
                body.addView(review, ui.fullWidthParams(ui.dp(8)));
            }
        }

        EditText search = ui.searchInput("PriceTrace 브랜드·상품명 검색");
        search.setText(food.displayName());
        search.setSelection(search.length());
        Button searchButton = ui.primaryButton("상품 검색", null);
        TextView resultStatus = ui.text(
                priceTraceConfigured
                        ? "이름은 후보를 좁히는 용도입니다."
                        : "설정에서 PriceTrace 읽기 전용 DB를 먼저 연결하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        LinearLayout results = new LinearLayout(host.activity());
        results.setOrientation(LinearLayout.VERTICAL);

        if (isPublic) {
            forms.disabled(search, true);
            forms.disabled(searchButton, true);
            resultStatus.setText("상품 연결을 바꾸려면 PriceTrace 공개를 먼저 취소하세요.");
        } else if (!priceTraceConfigured) {
            forms.disabled(search, true);
            forms.disabled(searchButton, true);
        }

        body.addView(forms.sectionTitle("새 상품 연결 또는 변경"), ui.fullWidthParams(ui.dp(4)));
        body.addView(forms.field("상품 검색", search), ui.fullWidthParams(ui.dp(4)));
        body.addView(searchButton, ui.fullWidthParams(ui.dp(8)));
        body.addView(resultStatus, ui.fullWidthParams(ui.dp(8)));
        if (!priceTraceConfigured) {
            body.addView(ui.secondaryButton("PriceTrace 연결 설정 열기", v -> {
                dismissActiveDialog();
                host.openSettingsConnections();
            }), ui.fullWidthParams(ui.dp(8)));
        }
        body.addView(results, ui.fullWidthParams(ui.dp(4)));

        searchButton.setOnClickListener(v -> {
            String query = FitnessUi.inputText(search).trim();
            if (query.isEmpty()) {
                host.toast("검색할 상품명을 입력하세요.");
                return;
            }
            forms.loading(searchButton, true, "PriceTrace 상품을 불러오는 중");
            resultStatus.setText("PriceTrace 상품 정보를 조회하는 중…");
            results.removeAllViews();
            host.searchPriceTraceProducts(query, new ScreenHost.ProductSearchCallback() {
                @Override
                public void onComplete(List<ProductReadV1> products) {
                    host.activity().runOnUiThread(() -> {
                        forms.loading(searchButton, false, null);
                        renderResults(food, products, results, resultStatus);
                    });
                }

                @Override
                public void onError(Exception error) {
                    host.activity().runOnUiThread(() -> {
                        forms.loading(searchButton, false, null);
                        resultStatus.setText(error.getMessage() == null
                                ? "PriceTrace 상품 조회에 실패했습니다."
                                : error.getMessage());
                    });
                }
            });
        });

        ScrollView scroll = new ScrollView(host.activity());
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        activeDialog = ui.bottomSheet(
                food.name + " · PriceTrace 연결",
                scroll,
                "닫기",
                () -> { },
                null,
                null
        );
    }

    void showDiningOutPublication(NutritionFood food) {
        dismissActiveDialog();
        boolean isPublic = repository.isFoodPublic(food.id);
        boolean hasExactIdentity = hasExactDiningOutIdentity(food);
        boolean needsIdentityRepair = hasPriceTraceIdentity(food) && !hasExactIdentity;
        boolean priceTraceAuthenticated = host.priceTraceSupabaseConfig().isConfigured();
        LinearLayout body = forms.column();
        body.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16));
        body.addView(ui.text(
                "식당·메뉴 공개는 일반 완제품 공개와 분리됩니다. 선택한 PriceTrace 식당·지점·메뉴 ID를 그대로 사용하며 이름으로 연결하지 않습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        body.addView(ui.keyValue(
                "공개 상태",
                isPublic ? "PT 공개" : "개인 식당 메뉴"
        ));
        body.addView(ui.keyValue(
                "정확한 연결 정보",
                hasExactIdentity
                        ? "연결됨"
                        : needsIdentityRepair
                        ? "연결됨 · 지점 원본 코드 확인 필요"
                        : "없음"
        ));
        body.addView(ui.keyValue(
                "PT 관리자 세션",
                priceTraceAuthenticated ? "연결됨" : "로그인 필요"
        ));

        Button publication = ui.button(
                isPublic
                        ? "PT 공개 취소"
                        : (hasExactIdentity || needsIdentityRepair
                        ? "PT에 공개"
                        : "PT에 식당·메뉴 등록 후 공개"),
                !isPublic && priceTraceAuthenticated,
                v -> confirmDiningOutPublication(food, !isPublic)
        );
        forms.disabled(publication, !(isPublic || priceTraceAuthenticated));
        body.addView(publication, ui.fullWidthParams(ui.dp(8)));
        body.addView(ui.text(
                !priceTraceAuthenticated
                        ? "설정에서 PriceTrace 관리자 계정으로 로그인하세요."
                        : hasExactIdentity
                        ? "공개하면 기존 PT 식당에 메뉴만 연결합니다."
                        : needsIdentityRepair
                        ? "기존 PT 지점에서 원본 코드를 확인한 뒤 연결 정보를 보완하고 공개합니다."
                        : "공개하면 PT에 식당·지점·메뉴를 함께 등록한 뒤 공개합니다.",
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        ));

        ScrollView scroll = new ScrollView(host.activity());
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        activeDialog = ui.bottomSheet(
                food.brand + " · " + food.name + " · PT 공개",
                scroll,
                "닫기",
                () -> { },
                null,
                null
        );
    }

    private boolean hasExactDiningOutIdentity(NutritionFood food) {
        JSONObject identity = parseDiningOutIdentity(food);
        if (identity == null) {
            return false;
        }
        return hasPriceTraceIdentity(identity) && hasText(identity, "source_location_code");
    }

    private boolean hasPriceTraceIdentity(NutritionFood food) {
        JSONObject identity = parseDiningOutIdentity(food);
        return identity != null && hasPriceTraceIdentity(identity);
    }

    private boolean hasPriceTraceIdentity(JSONObject identity) {
        String contract = identity.optString(
                "schema_version",
                identity.optString("contract_version", "")
        );
        return "dining-out-identity.v1".equals(contract)
                && hasText(identity, "restaurant_id")
                && hasText(identity, "restaurant_location_id")
                && hasText(identity, "restaurant_menu_id")
                && hasText(identity, "catalog_product_id");
    }

    private JSONObject parseDiningOutIdentity(NutritionFood food) {
        if (food == null || food.sourceReference == null) {
            return null;
        }
        try {
            return new JSONObject(food.sourceReference);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private boolean hasText(JSONObject object, String key) {
        return !object.optString(key, "").trim().isEmpty();
    }

    private void confirmDiningOutPublication(NutritionFood food, boolean publish) {
        String title = publish ? "PT에 식당 메뉴 공개" : "PT 식당 메뉴 공개 취소";
        String message = publish
                ? food.brand + " · " + food.name + "을(를) PT에서 공개할까요?"
                : food.brand + " · " + food.name + "을(를) PT에서 숨길까요?";
        ui.confirmSheet(
                title,
                message,
                publish
                        ? "선택한 식당·메뉴 연결 정보로 공개 상태를 변경합니다."
                        : "기존 공개 상태만 취소하며 로컬 영양 기록은 유지됩니다.",
                publish ? "공개" : "공개 취소",
                () -> setDiningOutPublication(food, publish)
        );
    }

    private void setDiningOutPublication(NutritionFood food, boolean publish) {
        if (publicationUpdating) {
            return;
        }
        publicationUpdating = true;
        dismissActiveDialog();
        host.toast(publish ? "PT 식당 메뉴를 공개하는 중입니다." : "PT 식당 메뉴 공개를 취소하는 중입니다.");
        host.setDiningOutMenuPublication(
                food.id,
                publish,
                new NutritionCatalogRepository.PublicationCallback() {
                    @Override
                    public void onComplete(NutritionCatalogRepository.PublicationState state) {
                        host.activity().runOnUiThread(() -> {
                            publicationUpdating = false;
                            host.toast(state.isPublic
                                    ? "PT에 식당 메뉴를 공개했습니다."
                                    : "PT 식당 메뉴 공개를 취소했습니다.");
                            showDiningOutPublication(food);
                            host.rerender();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        host.activity().runOnUiThread(() -> {
                            publicationUpdating = false;
                            showDiningOutPublication(food);
                            host.toast(error.getMessage() == null
                                    ? "PT 식당 메뉴 공개 상태를 변경하지 못했습니다."
                                    : error.getMessage());
                        });
                    }
                }
        );
    }

    private void confirmUnlink(NutritionFood food, ProductNutritionLink approved) {
        if (repository.isFoodPublic(food.id)) {
            host.toast("PriceTrace 공개를 먼저 취소한 뒤 상품 연결을 해제하세요.");
            return;
        }
        ui.confirmSheet(
                "PriceTrace 연결 해제",
                approved.displayLabel() + " 연결을 해제합니다.",
                "영양 정보와 과거 식사 기록은 유지됩니다.",
                "연결 해제",
                () -> {
                    if (!repository.unlinkProduct(food.id)) {
                        host.toast("이미 해제되었거나 연결 정보를 찾을 수 없습니다.");
                        return;
                    }
                    syncLinksQuietly();
                    host.toast("상품 연결만 해제했습니다. 영양 정보와 과거 식사 기록은 유지됩니다.");
                    dismissActiveDialog();
                    host.rerender();
                }
        );
    }

    private void renderResults(
            NutritionFood food,
            List<ProductReadV1> products,
            LinearLayout results,
            TextView status
    ) {
        results.removeAllViews();
        if (products == null || products.isEmpty()) {
            status.setText("검색 결과가 없습니다. 영양 항목은 연결 없이 계속 사용할 수 있습니다.");
            return;
        }
        status.setText(products.size() + "개 표준상품 · 하위 규격은 표시하지 않습니다.");
        for (ProductReadV1 product : products) {
            Button choice = ui.button(product.standardProductLabel(), false, v ->
                    selectStandardProduct(food, product));
            choice.setAllCaps(false);
            choice.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_START);
            results.addView(choice, ui.fullWidthParams(ui.dp(7)));
        }
    }

    private void selectStandardProduct(NutritionFood food, ProductReadV1 standardProduct) {
        ProductReadV1 exactProduct = standardProduct.exactVariantForBasis(
                food.basisAmount,
                food.basisUnit
        );
        if (exactProduct == null) {
            LinearLayout body = forms.column();
            body.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16));
            body.addView(ui.text(
                    standardProduct.standardProductLabel()
                            + "은 확인했지만 입력된 영양 기준량과 유일하게 일치하는 규격이 없습니다. "
                            + "하위 규격을 임의로 연결하지 않으며 영양 정보는 그대로 사용할 수 있습니다.",
                    14,
                    FitnessUi.COLOR_TEXT,
                    false
            ));
            ui.bottomSheet("표준상품 확인", body, "확인", () -> { }, null, null);
            return;
        }
        confirmExactSelection(food, exactProduct);
    }

    private void confirmExactSelection(NutritionFood food, ProductReadV1 product) {
        if (repository.isFoodPublic(food.id)) {
            host.toast("PriceTrace 공개를 먼저 취소한 뒤 상품 연결을 변경하세요.");
            return;
        }
        ui.confirmSheet(
                "표준상품 연결 확인",
                product.standardProductLabel()
                        + "\n\n이 PriceTrace 상품을 " + food.displayName() + "에 연결할까요?",
                "연결 후에도 영양 정보와 과거 식사 기록은 유지됩니다.",
                "연결",
                () -> {
                    repository.linkProduct(food.id, product);
                    syncLinksQuietly();
                    host.toast("선택한 표준상품을 연결했습니다.");
                    dismissActiveDialog();
                    host.rerender();
                }
        );
    }

    private void confirmPublication(
            NutritionFood food,
            ProductNutritionLink approved,
            boolean publish
    ) {
        String title = publish ? "PriceTrace에 공개" : "PriceTrace 공개 취소";
        String message = publish
                ? food.displayName() + "의 영양정보가 인터넷에서 공개됩니다.\n\n"
                + approved.displayLabel() + "에 공개할까요?"
                : food.displayName() + "의 영양정보를 PriceTrace에서 숨길까요?";
        ui.confirmSheet(
                title,
                message,
                publish
                        ? "공개 후에는 연결과 영양값을 변경하려면 먼저 공개를 취소해야 합니다."
                        : "공개 상태만 취소하며 로컬 영양 정보와 과거 기록은 유지됩니다.",
                publish ? "공개" : "공개 취소",
                () -> setPublication(food, approved, publish)
        );
    }

    private void setPublication(
            NutritionFood food,
            ProductNutritionLink approved,
            boolean publish
    ) {
        if (publicationUpdating) {
            return;
        }
        publicationUpdating = true;
        dismissActiveDialog();
        host.toast(publish ? "영양정보를 공개하는 중입니다." : "영양정보 공개를 취소하는 중입니다.");
        host.setNutritionFoodPublication(
                food.id,
                approved.catalogProductId,
                publish,
                new NutritionCatalogRepository.PublicationCallback() {
                    @Override
                    public void onComplete(NutritionCatalogRepository.PublicationState state) {
                        host.activity().runOnUiThread(() -> {
                            publicationUpdating = false;
                            host.toast(state.isPublic
                                    ? "영양정보를 PriceTrace에 공개했습니다."
                                    : "PriceTrace 공개를 취소했습니다.");
                            dismissActiveDialog();
                            show(food);
                            host.rerender();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        host.activity().runOnUiThread(() -> {
                            publicationUpdating = false;
                            show(food);
                            host.toast(error.getMessage() == null
                                    ? "영양정보 공개 상태를 변경하지 못했습니다."
                                    : error.getMessage());
                        });
                    }
                }
        );
    }

    private void loadSuggestion(NutritionFood food, ProductNutritionLink suggestion) {
        host.toast("제안된 표준상품을 PriceTrace에서 확인합니다.");
        host.loadPriceTraceProduct(suggestion.catalogProductId, new ScreenHost.ProductLoadCallback() {
            @Override
            public void onComplete(ProductReadV1 product) {
                host.activity().runOnUiThread(() -> {
                    if (product == null) {
                        host.toast("PriceTrace에서 제안 상품을 찾지 못해 승인하지 않았습니다.");
                        return;
                    }
                    confirmSuggestion(food, suggestion, product);
                });
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> host.toast(
                        error.getMessage() == null
                                ? "PriceTrace 제안을 확인하지 못했습니다."
                                : error.getMessage()
                ));
            }
        });
    }

    private void refreshApprovedProduct(NutritionFood food, ProductNutritionLink approved) {
        host.loadPriceTraceProduct(approved.catalogProductId, new ScreenHost.ProductLoadCallback() {
            @Override
            public void onComplete(ProductReadV1 product) {
                host.activity().runOnUiThread(() -> {
                    if (product == null) {
                        host.toast("PriceTrace에서 현재 연결 상품을 찾지 못했습니다.");
                        return;
                    }
                    dismissActiveDialog();
                    show(food);
                    host.toast("판매처·최근 관측가·관측시각을 새로고침했습니다.");
                });
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> host.toast(
                        error.getMessage() == null
                                ? "PriceTrace 상품 정보를 새로고침하지 못했습니다."
                                : error.getMessage()
                ));
            }
        });
    }

    private void confirmSuggestion(
            NutritionFood food,
            ProductNutritionLink suggestion,
            ProductReadV1 product
    ) {
        if (repository.isFoodPublic(food.id)) {
            host.toast("PriceTrace 공개를 먼저 취소한 뒤 상품 연결을 변경하세요.");
            return;
        }
        ui.sheetWithSecondary(
                "PriceTrace 제안 검토",
                ui.text(
                        product.exactSelectionLabel()
                                + "\n\n영양 항목: " + food.name
                                + "\n제안 참조: " + (suggestion.proposalReference == null
                                ? "없음" : suggestion.proposalReference),
                        14,
                        FitnessUi.COLOR_TEXT,
                        false
                ),
                "제안 승인",
                () -> {
                    repository.approveProductSuggestion(suggestion.id, product);
                    syncLinksQuietly();
                    host.toast("PriceTrace 제안을 승인했습니다.");
                    dismissActiveDialog();
                    host.rerender();
                },
                "제안 거절",
                () -> {
                    if (repository.rejectProductSuggestion(suggestion.id)) {
                        syncLinksQuietly();
                        host.toast("PriceTrace 제안을 거절했습니다.");
                        dismissActiveDialog();
                        host.rerender();
                    }
                }
        );
    }

    private void dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }
        activeDialog = null;
    }

    private void syncLinksQuietly() {
        host.syncNutritionCatalog(new NutritionCatalogRepository.SyncCallback() {
            @Override
            public void onComplete(int pushedRows, int pulledRows) {
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> host.toast(
                        "연결 결정은 기기에 저장했지만 Nutrition DB 동기화는 실패했습니다."
                ));
            }
        });
    }
}
