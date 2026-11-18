import random
from urllib.parse import quote_plus

from faker import Faker

from features import values

LOCALES = ("en_US", "vi_VN", "es_ES", "fr_FR", "ja_JP")
PATTERNS = ["{a}", "{a} {b}", "{a}: {b}", "{a}'s {b}", "{a} & {b}", "{a} ({y})", "{a} - {b}",
            "{a} {n}", "{a}: {b} {c}", "the {a}", "{a} và {b}", "{a}, {b}", "{a}!", "{a}?", "{a}..."]


def generate(n=6000, seed=42):
    rng = random.Random(seed)
    fakers = [Faker(loc) for loc in LOCALES]
    for i, f in enumerate(fakers):
        f.seed_instance(seed + i)

    def title(f):
        a, b, c = f.word(), f.word(), f.word()
        t = rng.choice(PATTERNS).format(a=a, b=b, c=c, y=rng.randint(1950, 2030), n=rng.randint(1, 9))
        return t.title() if rng.random() < 0.5 else t

    makers = [
        title,
        lambda f: f.name(),
        lambda f: f.email(),
        lambda f: f.user_name(),
        lambda f: f.city(),
        lambda f: f.street_address(),
        lambda f: f.sentence(nb_words=rng.randint(2, 8)),
        lambda f: f.word(),
        lambda f: rng.choice(["asc", "desc", "true", "false", "movie", "series", "anime", "vi", "en"]),
        lambda f: f.url() + f.uri_path(),
        lambda f: f.image_url(),
        lambda f: "/" + f.uri_path() + "/" + f.file_name(),
        lambda f: f"/uploads/{rng.choice(['images', 'videos'])}/{f.uuid4()[:rng.randint(4, 36)]}"
                  f"{rng.choice(['', '_', '-'])}{f.word()}.{rng.choice(['jpg', 'png', 'webp', 'mp4'])}",
        lambda f: f.user_name() + rng.choice(["", "_", "."]) + str(rng.randint(1, 9999)),
        lambda f: f.paragraph(nb_sentences=rng.randint(1, 3)),
    ]
    out = []
    for _ in range(n):
        f = rng.choice(fakers)
        out.extend(values("", "v=" + quote_plus(rng.choice(makers)(f))))
    return out
