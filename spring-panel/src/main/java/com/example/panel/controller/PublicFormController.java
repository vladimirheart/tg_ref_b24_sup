    @GetMapping("/{channelId}")
    public String view(@PathVariable String channelId,
                       @RequestParam(value = "token", required = false) String token,
                       @RequestParam(value = "dialog", required = false) String dialog,
                       Model model) {
        Optional<PublicFormConfig> config = publicFormService.loadConfig(channelId);
        if (config.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String initialToken = Optional.ofNullable(token).filter(t -> !t.isBlank()).orElse(dialog);
        model.addAttribute("channelId", config.get().channelId());
        model.addAttribute("channelRef", channelId);
        model.addAttribute("channelName", config.get().channelName());
        model.addAttribute("initialToken", Optional.ofNullable(initialToken).orElse(""));
        return "public/form";
