import argparse
import pickle
import time

from sklearn.metrics import roc_auc_score

from benign import generate
from dataset import load_httpparams, prepare
from models import MODELS, build_model, raw_scores


def request_scores(model, vals_list):
    uniq = sorted({v for vals in vals_list for v in vals})
    s = dict(zip(uniq, raw_scores(model, uniq))) if uniq else {}
    return [max((s[v] for v in vals), default=float("-inf")) for vals in vals_list]


def rate(scores, labels, t, want):
    picked = [s >= t for s, y in zip(scores, labels) if y == want]
    return sum(picked) / len(picked) if picked else 0.0


def main():
    ap = argparse.ArgumentParser(description="So sanh nhieu mo hinh tren cung du lieu va cung muc bao nham")
    ap.add_argument("--data", required=True)
    ap.add_argument("--params", default="data/HttpParamsDataset")
    ap.add_argument("--extra-normal")
    ap.add_argument("--models", default="lr,svm,nb,rf,ens")
    ap.add_argument("--max-fpr", type=float, default=0.001,
                    help="muc bao nham cho phep tren tap hieu chinh (mac dinh 0,1%)")
    args = ap.parse_args()

    train, test_req = prepare(args.data, args.params, args.extra_normal, verbose=False)
    hp = load_httpparams(args.params, "test")
    calib = generate(3000, seed=99)
    held = generate(3000, seed=7)
    csic_y = test_req.label.tolist()
    vals = test_req.vals.tolist()
    print(f"Train {len(train)} mau. Moi model duoc chon nguong rieng sao cho bao nham <= {args.max_fpr:.1%} "
          f"tren {len(calib)} gia tri binh thuong dung de hieu chinh (khong trung tap danh gia).\n")

    head = f"{'Model':<26}{'AUC CSIC':>9}{'AUC Param':>10}{'Recall CSIC':>12}{'Bao nham CSIC':>14}" \
           f"{'Recall Param':>13}{'Bao nham Param':>15}{'Bao nham Input':>15}{'Train (s)':>10}{'ms/gia tri':>11}{'MB':>7}"
    print(head)
    print("-" * len(head))
    for key in args.models.split(","):
        key = key.strip()
        model = build_model(key)
        t0 = time.time()
        model.fit(train.x, train.y)
        train_sec = time.time() - t0

        cal = sorted(raw_scores(model, calib))
        k = int(len(cal) * (1 - args.max_fpr))
        threshold = cal[min(k, len(cal) - 1)] + 1e-9

        cs = request_scores(model, vals)
        t0 = time.time()
        hs = raw_scores(model, hp.x.tolist())
        ms = (time.time() - t0) / len(hp) * 1000
        bs = raw_scores(model, held)
        size_mb = len(pickle.dumps(model)) / 1e6

        print(f"{MODELS[key]:<26}{roc_auc_score(csic_y, cs):>9.4f}{roc_auc_score(hp.y, hs):>10.4f}"
              f"{rate(cs, csic_y, threshold, 1):>12.1%}{rate(cs, csic_y, threshold, 0):>14.1%}"
              f"{rate(hs, hp.y.tolist(), threshold, 1):>13.1%}{rate(hs, hp.y.tolist(), threshold, 0):>15.2%}"
              f"{sum(s >= threshold for s in bs) / len(bs):>15.2%}{train_sec:>10.1f}{ms:>11.3f}{size_mb:>7.1f}")


if __name__ == "__main__":
    main()
