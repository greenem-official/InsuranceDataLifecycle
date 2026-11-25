"""Генератор искусственных CSV-наборов данных для страховой компании
(полисы КАСКО/ОСАГО, дела об убытках, клиентская база)
для демонстрации и тестирования системы экологии данных.
"""

import argparse
import csv
import os
import random
from datetime import date, timedelta

REGIONS = ["Москва", "Санкт-Петербург", "Казань", "Екатеринбург", "Новосибирск", "Краснодар"]
CAR_BRANDS = [
    ("Toyota", "Camry"), ("Kia", "Rio"), ("Hyundai", "Solaris"),
    ("Skoda", "Octavia"), ("Volkswagen", "Tiguan"), ("Lada", "Vesta"),
    ("Geely", "Monjaro"), ("Haval", "Jolion"), ("BMW", "X5"),
]
CLAIM_TYPES = ["ДТП (столкновение)", "Повреждение на парковке", "Скол лобового стекла", "Град / стихия", "Противоправные действия"]
CLAIM_STATUSES = ["Закрыто", "Выплачено", "На рассмотрении", "Отказ"]
LAST_NAMES = ["Иванов", "Смирнов", "Кузнецов", "Попов", "Соколов", "Лебедев", "Козлов", "Новиков", "Морозов", "Волков"]
FIRST_NAMES = ["Александр", "Дмитрий", "Максим", "Сергей", "Андрей", "Алексей", "Елена", "Ольга", "Наталья", "Татьяна"]
SEGMENTS = ["Розничный", "VIP", "Корпоративный", "Онлайн"]


def random_date(start_year: int = 2019, end_year: int = 2026) -> str:
    start = date(start_year, 1, 1)
    end = date(end_year, 11, 25)
    delta = (end - start).days
    return (start + timedelta(days=random.randint(0, delta))).isoformat()


def maybe_null(value, null_rate: float):
    return "" if random.random() < null_rate else value


def generate_policies_rows(count: int, policy_type: str, null_rate: float):
    headers = [
        "policy_number", "policy_type", "client_full_name", "client_phone",
        "region", "car_brand", "car_model", "car_year",
        "insured_sum_rub", "premium_rub", "start_date", "end_date", "status"
    ]
    rows = []
    for i in range(1, count + 1):
        brand, model = random.choice(CAR_BRANDS)
        start_str = random_date(2020, 2026)
        start_dt = date.fromisoformat(start_str)
        end_dt = start_dt + timedelta(days=365)
        insured_sum = random.randint(600_000, 6_500_000) if policy_type == "КАСКО" else 400_000
        premium = round(insured_sum * random.uniform(0.035, 0.085), 2) if policy_type == "КАСКО" else round(random.uniform(6_500, 24_000), 2)
        full_name = f"{random.choice(LAST_NAMES)} {random.choice(FIRST_NAMES)}"
        phone = f"+79{random.randint(100000000, 999999999)}"
        status = "Действует" if end_dt >= date(2026, 11, 25) else "Истёк"

        row = [
            f"{'KSK' if policy_type == 'КАСКО' else 'OSG'}-{start_dt.year}-{i:07d}",
            policy_type,
            maybe_null(full_name, null_rate),
            maybe_null(phone, null_rate),
            maybe_null(random.choice(REGIONS), null_rate),
            maybe_null(brand, null_rate),
            maybe_null(model, null_rate),
            maybe_null(random.randint(2014, 2026), null_rate),
            insured_sum,
            premium,
            start_str,
            end_dt.isoformat(),
            status,
        ]
        rows.append(row)
    return headers, rows


def generate_claims_rows(count: int, null_rate: float):
    headers = [
        "claim_id", "policy_number", "incident_date", "report_date",
        "claim_type", "region", "claimed_amount_rub", "approved_payout_rub",
        "adjuster_notes", "status"
    ]
    rows = []
    for i in range(1, count + 1):
        inc_str = random_date(2019, 2026)
        inc_dt = date.fromisoformat(inc_str)
        rep_dt = inc_dt + timedelta(days=random.randint(0, 14))
        claimed = round(random.uniform(15_000, 1_450_000), 2)
        status = random.choice(CLAIM_STATUSES)
        payout = 0.0 if status == "Отказ" else round(claimed * random.uniform(0.7, 1.0), 2)
        notes = f"Осмотр проведён, пакет документов №{random.randint(100, 9999)}"

        row = [
            f"CLM-{inc_dt.year}-{i:07d}",
            f"KSK-{inc_dt.year}-{random.randint(1, max(10, count)):07d}",
            inc_str,
            rep_dt.isoformat(),
            maybe_null(random.choice(CLAIM_TYPES), null_rate),
            maybe_null(random.choice(REGIONS), null_rate),
            claimed,
            maybe_null(payout, null_rate),
            maybe_null(notes, null_rate),
            status,
        ]
        rows.append(row)
    return headers, rows


def generate_clients_rows(count: int, null_rate: float):
    headers = [
        "client_id", "full_name", "birth_date", "email",
        "phone", "region", "segment", "active_policies_count", "last_contact_date"
    ]
    rows = []
    for i in range(1, count + 1):
        full_name = f"{random.choice(LAST_NAMES)} {random.choice(FIRST_NAMES)}"
        row = [
            f"CLT-{i:07d}",
            full_name,
            maybe_null(random_date(1960, 2004), null_rate),
            maybe_null(f"client{i}@example.ru", null_rate),
            maybe_null(f"+79{random.randint(100000000, 999999999)}", null_rate),
            maybe_null(random.choice(REGIONS), null_rate),
            maybe_null(random.choice(SEGMENTS), null_rate),
            random.randint(0, 5),
            maybe_null(random_date(2019, 2026), null_rate),
        ]
        rows.append(row)
    return headers, rows


def inject_duplicates(rows: list, dup_rate: float) -> list:
    if dup_rate <= 0 or not rows:
        return rows
    dup_count = int(len(rows) * dup_rate)
    duplicates = [list(random.choice(rows)) for _ in range(dup_count)]
    combined = rows + duplicates
    random.shuffle(combined)
    return combined


def write_csv(filepath: str, headers: list, rows: list):
    os.makedirs(os.path.dirname(filepath) or ".", exist_ok=True)
    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f, delimiter=";")
        writer.writerow(headers)
        writer.writerows(rows)
    size_mb = os.path.getsize(filepath) / (1024 * 1024)
    print(f"[OK] {filepath}: {len(rows)} строк, размер {size_mb:.2f} МБ")


def main():
    parser = argparse.ArgumentParser(description="Генератор тестовых CSV-документов страховой компании")
    parser.add_argument("--out", default="generated_datasets", help="Папка для сохранения CSV")
    parser.add_argument("--rows", type=int, default=25000, help="Базовое число строк в каждом файле")
    parser.add_argument("--dup-rate", type=float, default=0.12, help="Доля дубликатов (0.0 - 0.5)")
    parser.add_argument("--null-rate", type=float, default=0.08, help="Доля пустых полей (0.0 - 0.5)")
    parser.add_argument("--seed", type=int, default=42, help="Seed генератора случайных чисел")
    args = parser.parse_args()

    random.seed(args.seed)

    datasets = [
        ("kasko_policies_2025.csv", *generate_policies_rows(args.rows, "КАСКО", args.null_rate)),
        ("osago_policies_2025.csv", *generate_policies_rows(int(args.rows * 1.4), "ОСАГО", args.null_rate * 0.5)),
        ("insurance_claims_archive.csv", *generate_claims_rows(int(args.rows * 0.8), args.null_rate * 1.5)),
        ("clients_crm_export.csv", *generate_clients_rows(args.rows, args.null_rate)),
    ]

    for filename, headers, rows in datasets:
        rows_with_dups = inject_duplicates(rows, args.dup_rate)
        write_csv(os.path.join(args.out, filename), headers, rows_with_dups)


if __name__ == "__main__":
    main()