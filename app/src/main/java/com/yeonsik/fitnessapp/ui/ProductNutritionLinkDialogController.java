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

        body.addView(ui.text(
                "영양 정보는 이 연결 없이도 독립적으로 저장됩니다. 이름 자동 매칭은 하지 "
                        + "않으며 아래 표준상품 후보에서 연결할 항목을 직접 선택해야 합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));

        if (approved == null) {
            body.addView(ui.keyValue("현재 연결", "없음 (영양 단독 항목)"));
        } else {
            body.addView(ui.fieldLabel("현재 승인 연결"));
            body.addView(ui.text(approved.displayLabel(), 12, FitnessUi.COLOR_TEXT, false));
            Button refresh = ui.button("상품·가격 새로고침", false,
                    v -> refreshApprovedProduct(food, approved));
            Button unlink = ui.button("PriceTrace 연결 해제", false, v -> {
                if (repository.unlinkProduct(food.id)) {
                    syncLinksQuietly();
                    host.toast("상품 연결만 해제했습니다. 영양 정보와 과거 식사 snapshot은 유지됩니다.");
                    dismissActiveDialog();
                    host.rerender();
                }
            });
            body.addView(ui.buttonRow(refresh, unlink), ui.fullWidthParams(ui.dp(10)));
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
                host.priceTraceSupabaseConfig().isConnectionConfigured()
                        ? "이름은 후보를 좁히는 용도입니다."
                        : "설정에서 PriceTrace 읽기 전용 DB를 먼저 연결하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        LinearLayout results = new LinearLayout(host.activity());
        results.setOrientation(LinearLayout.VERTICAL);

        body.addView(ui.fieldLabel("새 상품 연결 또는 변경"));
        body.addView(search, ui.fullWidthParams(ui.dp(8)));
        body.addView(searchButton, ui.fullWidthParams(ui.dp(8)));
        body.addView(resultStatus, ui.fullWidthParams(ui.dp(8)));
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
        status.setText(products.size() + "개 표준상품 후보 · 브랜드와 상품 이름만 표시합니다.");
        for (ProductReadV1 product : products) {
            Button choice = ui.button(product.standardProductLabel(), false, v ->
                    confirmExactSelection(food, product));
            choice.setAllCaps(false);
            choice.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_START);
            results.addView(choice, ui.fullWidthParams(ui.dp(7)));
        }
    }

    private void confirmExactSelection(NutritionFood food, ProductReadV1 product) {
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
        new AlertDialog.Builder(host.activity())
                .setTitle("PriceTrace 제안 검토")
                .setMessage(product.standardProductLabel()
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
