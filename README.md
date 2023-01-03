# How to run
## Step 1
Use docker to activate OneFrame service

## Step 2
Run ForexProxy
```
sbt run
```

## Step 3
Send request. e.g.
```
curl 'http://localhost:5005/rates?from=JPY&to=USD'
```



# Paidy/Forex Design Doc

## Requirement

The goal of this project is to create a proxy for getting exchange rate by calling OneFrame API. The proxy has following requirement:

- The service returns an exchange rate when provided with 2 supported currencies
- The rate should not be older than 5 minutes
- The service should support at least 10,000 successful requests per day with 1 API token

## Assumption

- Exchange rate is invertible. For example: Rate From CurrencyA to CurrencyB = 1 / (Rate from CurrencyB to Currency A)
- Three-pair Triangulation is valid. For example, if we know rate from CurrencyA to CurrencyB, and rate from currencyA to currencyC, then we can get the rate from currencyB to currencyC and vise versa
- Only 9 currencies that Currency.scala covers are supported in this project.

## Workflow

1. Forex receives curA and curB
2. Check cache to see whether it's cache hit
3. Cache hit, returning response
4. Cache miss, call OneFrame
5. OneFrame returns Prices, save it to cache
6. Return to requester

## Cache

OneFrame API only allows for 1000 requests per day, while we may get 10000 requests to Forex. Hence we need a mechanism to cache the exchange rate.

There are 9 supported currencies, which is 9 * 8 / 2 = 36 currency pairs. If everytime there’s a cache miss, we just get exact currency rate from requested currency pairs. In worst case we need 36 * 12(calls per hour) * 24 > 20000 calls, which is much larger than required 1000 calls per day. Hence we need to update more currency pairs in one call. 

**Option 1: Cache all currency pairs. When there’s cache miss, call OneFrame to update all currency pairs.**

In this case, cache key will be [currencyA, currencyB], while cache value is [exchangeRate, LastUpdateTimestamp]. We just need 12 * 24 = 288 calls per day. TTL is 5 mins as per requirement.

Pros: 

- Comparing to option 2, result exchange rate is more accurate.

Cons:

- More cache space comparing to option 2.
- URL has size limit(2048 characters), when supported currency increases as Forex service scales up, it will break.

**Option 2(Recommended): Only cache exchange rate based on one currency(e.g. USD). When there’s cache miss, call oneFrame to update currency pairs which includes the base currency. Use cross currency triangulation to calculate exact currency rate if necessary.**

In this case, cache key will be just one currency, as we just record the exchange rate with USD. Thus cache key is [CurrencyA], cache value is [exchangeRateWithUSD, LatestUpdateTimeStamp]. Given there’s 9 supported currencies(including USD). TTL is 5 mins as per requirement.

Pros:

- Less cache space comparing to option 1
- Capable of scale up when more currencies are supported. Given there are 195 countries all over the world. We only need to query for 194 pairs.

Cons:

- Result exchange is not as accurate as option 1
- Too much depend on base currency. If base currency is removed from OneFrame, it’s a significant break.

Reason to choose option 2: 

- As service allows for returning exchange rate not older than 5 mins, it’s tolerable to return not quite accurate result.
- The possiblity of missing base currency in OneFrame is rare.

## Failure Mode

Following error code and error response will be supported.

| ErrorMessage | ErrorCode | Scenario |
| --- | --- | --- |
| Invalid Currency Type | 400 | Currency type not existed in OneFrame |
| InternalError | 500 | Other unmodelled failure |