# Code Brief

Daily frontend news digest delivered to Slack. Collects from GitHub, Reddit, Hacker News, Dev.to, RSS, and npm. Curated by Gemini AI.

**Cost:** $0/month • **Schedule:** Daily at 7 AM Oslo time

## Setup

### 1. Get API Keys

**Gemini API:** https://makersuite.google.com/app/apikey

**Slack Webhook:**
1. Go to https://api.slack.com/apps
2. Create New App → From scratch
3. Incoming Webhooks → Activate → Add to workspace
4. Copy webhook URL

### 2. Deploy to GitHub

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/YOUR_USERNAME/code-brief.git
git push -u origin main
```

### 3. Add Secrets

Go to: **Settings → Secrets and variables → Actions → New repository secret**

Add:
- `GEMINI_API_KEY` - Your Gemini key
- `SLACK_WEBHOOK_URL` - Your Slack webhook

### 4. Run

**Automatic:** Runs daily at 7 AM Oslo time (configured in `.github/workflows/daily-digest.yml`)

**Manual:** Actions tab → Daily Digest → Run workflow

## Local Development

**Requirements:** Java 21, Maven 3.6+

```bash
# Set environment variables
export GEMINI_API_KEY="your-key"
export SLACK_WEBHOOK_URL="your-webhook"

# Run
./run-local.sh
```

Or with Maven:
```bash
mvn clean package
java -jar target/code-brief.jar
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Add more sources
github.trending.repos=react,vue,angular,svelte,next.js,remix
reddit.subreddits=javascript,reactjs,webdev,Frontend,angular,vuejs
rss.feeds=https://css-tricks.com/feed/,https://www.smashingmagazine.com/feed/

# Adjust filters
github.trending.min.stars=100
reddit.min.upvotes=50
hackernews.min.score=100
```

## Project Structure

```
src/main/java/com/codebrief/
├── CodeBriefApplication.java          # Main app
├── config/AppConfig.java              # HTTP & retry config
├── model/                             # NewsItem, Digest
├── collector/                         # 7 news collectors
└── service/
    ├── NewsCollectorService.java      # Orchestrates collection
    ├── GeminiService.java             # AI processing
    ├── SlackService.java              # Slack delivery
    └── DigestRunner.java              # Main runner
```

## Testing

Run all tests:
```bash
mvn test
```

Run specific service tests:
```bash
# Test NewsCollectorService
./run-tests.sh collector

# Test GeminiService
./run-tests.sh gemini

# Test SlackService
./run-tests.sh slack
```

Or with Maven:
```bash
mvn test -Dtest=NewsCollectorServiceTest
mvn test -Dtest=GeminiServiceTest
mvn test -Dtest=SlackServiceTest
```

## Troubleshooting

**Check logs:** Actions tab → Click failed run → View logs

**Test Slack webhook:**
```bash
curl -X POST -H 'Content-type: application/json' \
  --data '{"text":"Test"}' YOUR_WEBHOOK_URL
```

**Common issues:**
- Missing/incorrect secrets → Verify in Settings → Secrets
- API rate limits → Check error logs
- No news collected → Normal on some days, check filters

## License

MIT
