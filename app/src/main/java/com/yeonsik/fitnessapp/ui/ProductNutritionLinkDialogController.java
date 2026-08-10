package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.ProductNutritionLink;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import java.util.List;

/** Explicit selection, suggestion approval, and unlink UI for cross-project product links. */
final class ProductNutritionLinkDialogController {
    private final ScreenHost host;
    private final FitnessUi ui;
    private final NutritionCatalogRepository repository;
    private AlertDialog activeDialog;
    private boolean publicationUpdating;

    ProductNutritionLinkDialogController(ScreenHost host) {
        this.host = host;
        this.ui = host.ui();
        this.repository = host.nutritionCatalogRepository();
    }

    void show(NutritionFood food) {
        dismissActiveDialog();
        LinearLayout body = ui.form();
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
            body.addView(ui.fieldLabel("현재 승인 연결"));
            body.addView(ui.text(approved.displayLabel(), 12, FitnessUi.COLOR_TEXT, false));
            Button refresh = ui.button("상품·가격 새로고침", false,
                    v -> refreshApprovedProduct(food, approved));
            refresh.setEnabled(priceTraceConfigured);
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
            body.addView(ui.fieldLabel("PriceTrace 제안"));
            for (ProductNutritionLink suggestion : suggestions) {
                Button review = ui.button(
                        "제안 검토 · " + suggestion.displayLabel(),
                        false,
                        v -> loadSuggestion(food, suggestion)
                );
                body.addView(review, ui.fullWidthParams(ui.dp(8)));
            }
        }

        EditText search = ui.searchField("PriceTrace 브랜드·상품명 검색");
        search.setText(food.displayName());
        search.setSelection(search.length());
        Button searchButton = ui.button("상품 검색", true, null);
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
            search.setEnabled(false);
            searchButton.setEnabled(false);
            resultStatus.setText("상품 연결을 바꾸려면 PriceTrace 공개를 먼저 취소하세요.");
        } else if (!priceTraceConfigured) {
            search.setEnabled(false);
            searchButton.setEnabled(false);
        }

        body.addView(ui.fieldLabel("새 상품 연결 또는 변경"));
        body.addView(search, ui.fullWidthParams(ui.dp(8)));
        body.addView(searchButton, ui.fullWidthParams(ui.dp(8)));
        body.addView(resultStatus, ui.fullWidthParams(ui.dp(8)));
        if (!priceTraceConfigured) {
            body.addView(ui.button("PriceTrace 연결 설정 열기", false, v -> {
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
            searchButton.setEnabled(false);
            resultStatus.setText("product-read.v1 조회 중…");
            results.removeAllViews();
            host.searchPriceTraceProducts(query, new ScreenHost.ProductSearchCallback() {
                @Override
                public void onComplete(List<ProductReadV1> products) {
                    host.activity().runOnUiThread(() -> {
                        searchButton.setEnabled(true);
                        renderResults(food, products, results, resultStatus);
                    });
                }

                @Override
                public void onError(Exception error) {
                    host.activity().runOnUiThread(() -> {
                        searchButton.setEnabled(true);
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
        activeDialog = new AlertDialog.Builder(host.activity())
                .setTitle(food.name + " · PriceTrace 연결")
                .setView(scroll)
                .setNegativeButton("닫기", null)
                .create();
        activeDialog.show();
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
            new AlertDialog.Builder(host.activity())
                    .setTitle("표준상품 확인")
                    .setMessage(standardProduct.standardProductLabel()
                            + "은 확인했지만 입력된 영양 기준량과 유일하게 일치하는 규격이 없습니다. "
                            + "하위 규격을 임의로 연결하지 않으며 영양 정보는 그대로 사용할 수 있습니다.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        confirmExactSelection(food, exactProduct);
    }

    private void confirmExactSelection(NutritionFood food, ProductReadV1 product) {
        if (repository.isFoodPublic(food.id)) {
            host.toast("PriceTrace 공개를 먼저 취소한 뒤 상품 연결을 변경하세요.");
            return;
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("표준상품 연결 확인")
                .setMessage(product.standardProductLabel()
                        + "\n\n이 PriceTrace 상품을 " + food.displayName() + "에 연결할까요?")
                .setPositiveButton("연결", (dialog, which) -> {
                    repository.linkProduct(food.id, product);
                    syncLinksQuietly();
                    host.toast("선택한 표준상품을 연결했습니다.");
                    dismissActiveDialog();
                    host.rerender();
                })
                .setNegativeButton("취소", null)
                .show();
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
        new AlertDialog.Builder(host.activity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(publish ? "공개" : "공개 취소", (dialog, which) ->
                        setPublication(food, approved, publish))
                .setNegativeButton("취소", null)
                .show();
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
        new AlertDialog.Builder(host.activity())
                .setTitle("PriceTrace 제안 검토")
                .setMessage(product.exactSelectionLabel()
                        + "\n\n영양 항목: " + food.name
                        + "\n제안 참조: " + (suggestion.proposalReference == null
                        ? "없음" : suggestion.proposalReference))
                .setPositiveButton("제안 승인", (dialog, which) -> {
                    repository.approveProductSuggestion(suggestion.id, product);
                    syncLinksQuietly();
                    host.toast("PriceTrace 제안을 승인했습니다.");
                    dismissActiveDialog();
                    host.rerender();
                })
                .setNeutralButton("제안 거절", (dialog, which) -> {
                    if (repository.rejectProductSuggestion(suggestion.id)) {
                        syncLinksQuietly();
                        host.toast("PriceTrace 제안을 거절했습니다.");
                        dismissActiveDialog();
                        host.rerender();
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
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
