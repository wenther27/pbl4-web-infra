from sklearn.ensemble import RandomForestClassifier, VotingClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import ComplementNB
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC

MODELS = {
    "lr": "Logistic Regression",
    "svm": "Linear SVM",
    "nb": "Naive Bayes (Complement)",
    "rf": "Random Forest",
    "ens": "LR + RF (trung binh)",
}


def _tfidf(max_features):
    return TfidfVectorizer(analyzer="char", ngram_range=(1, 5), min_df=2,
                           max_features=max_features, sublinear_tf=True)


def build_model(name="lr"):
    if name == "lr":
        clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
        return Pipeline([("tfidf", _tfidf(300_000)), ("clf", clf)])
    if name == "svm":
        clf = LinearSVC(class_weight="balanced", C=0.5)
        return Pipeline([("tfidf", _tfidf(300_000)), ("clf", clf)])
    if name == "nb":
        return Pipeline([("tfidf", _tfidf(300_000)), ("clf", ComplementNB(alpha=0.3))])
    if name == "rf":
        clf = RandomForestClassifier(n_estimators=150, min_samples_leaf=2, class_weight="balanced",
                                     n_jobs=-1, random_state=42)
        return Pipeline([("tfidf", _tfidf(20_000)), ("clf", clf)])
    if name == "ens":
        return VotingClassifier([("lr", build_model("lr")), ("rf", build_model("rf"))], voting="soft")
    raise SystemExit(f"Model khong ho tro: {name}. Chon: {', '.join(MODELS)}")


def raw_scores(model, texts):
    if hasattr(model, "predict_proba"):
        return model.predict_proba(texts)[:, 1]
    return model.decision_function(texts)
