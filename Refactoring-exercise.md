# Application Refactoring

We have a monolithic application that requires a full recompile and redeploy every time we update it. 

We have multiple teams that manage elements of this application, and it's getting messy to manage updates without stepping on each others toes.

Lets refactor into microservices to align to teams

## Step 1: Understanding

### What is our goal? 

Understand ways we can decouple this monolith and create a plan to execute based on one that best aligns with our needs. 

### Prompts

/explain the components of this application. 

-- This is now in our context history. Lets save this and come back to it later. 
-- Already done in the docs so we can add that to our context for the next step.

# Step 2: Planning

## What is our goal? 

Step into /planning mode and figure out what our best course of action is here. 

### Prompts 

Using the `/plan` agent: 

I'd like to approach refactoring this into microservices by allowing for logical grouping of modules. Give me few choices for ways to align these microservices. Save the output in the REFACTORING directory

### Outcome

Option A (3-Service) — Maps 1:1 to existing packages. Recommended for most teams.

Option B (2-Service) — Simplest migration; keeps Order + Product together to preserve ACID transactions. Good interim step.

Option C (4-Service) — Splits Product into Catalog + Inventory for maximum scalability. Best for large orgs.

# Step 3: Execute the plan in phases

How should we proceed? Which one makes sense, are there factors here that we haven't thought about? 

### Additional reading

Take a legacy .NET Framework monolith and modernize it to the latest .NET release
https://github.com/Azure-Samples/modernize-monolith-workshop

Using GitHub Copilot to refactor code
https://github.blog/ai-and-ml/github-copilot/how-to-refactor-code-with-github-copilot/